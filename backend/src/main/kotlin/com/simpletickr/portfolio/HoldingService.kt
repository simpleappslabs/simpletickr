package com.simpletickr.portfolio

import com.simpletickr.portfolio.model.AccountHolding
import com.simpletickr.portfolio.model.Holding
import com.simpletickr.portfolio.persistence.HoldingRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.SplitAdjuster
import com.simpletickr.transaction.model.TransactionReplay
import com.simpletickr.transaction.model.TransactionType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class HoldingService(private val holdingRepository: HoldingRepository) {

    // WAC holdings per (assetId, listingId). Closed positions (net qty ≤ 0) are excluded.
    // Split transactions are consumed to adjust prior BUY/SELL quantities and prices.
    // `asOf`, when given, computes holdings as they stood on that date — used by
    // RecordTransferUseCase so a backdated transfer is validated against what was actually
    // held then, not what's held "now".
    fun getHoldings(portfolioId: Long, asOf: LocalDate? = null): List<Holding> {
        data class Key(
            val assetId: Long, val assetName: String,
            val listingId: Long, val exchange: String?, val ticker: String, val currency: CurrencyCode,
        )

        val transferFeesByListingId = holdingRepository.findTransferFeeRows(portfolioId, asOf).groupBy { it.listingId }

        return holdingRepository.findTransactionRows(portfolioId, asOf)
            .groupBy { Key(it.assetId, it.assetName, it.listingId, it.exchange, it.ticker, it.currency) }
            .mapNotNull { (key, rows) ->
                val splitIndex = TransactionReplay.splitIndex(
                    rows.filter { it.type == TransactionType.SPLIT }.map { Triple(it.listingId, it.date, it.quantity) },
                )

                val regulars = rows.filter { it.type != TransactionType.SPLIT }

                val netQty = regulars.fold(BigDecimal.ZERO) { acc, r ->
                    acc + TransactionReplay.signedQuantityDelta(r.listingId, r.date, r.quantity, r.type, splitIndex)
                }
                // A transfer moves custody, not portfolio inventory — only its fee (if any) is a
                // genuine reduction in what the portfolio holds.
                val transferFeeQty = (transferFeesByListingId[key.listingId] ?: emptyList()).fold(BigDecimal.ZERO) { acc, feeRow ->
                    acc + TransactionReplay.splitAdjustedQuantity(feeRow.listingId, feeRow.date, feeRow.feeQuantity, splitIndex)
                }
                val adjustedNetQty = netQty - transferFeeQty
                if (adjustedNetQty <= BigDecimal.ZERO) return@mapNotNull null

                val buys = regulars.filter { it.type == TransactionType.BUY }
                val totalBuyQty = buys.sumOf { r ->
                    TransactionReplay.splitAdjustedQuantity(r.listingId, r.date, r.quantity, splitIndex)
                }
                val totalBuyCost = buys.sumOf { r ->
                    val adj = SplitAdjuster.adjustmentFor(r.listingId, r.date, splitIndex)
                    val adjQty = r.quantity * adj.multiplier
                    val adjPrice = if (adj.multiplier == BigDecimal.ONE) r.price
                                   else r.price.divide(adj.multiplier, 10, RoundingMode.HALF_UP)
                    adjQty * adjPrice
                }
                val avgCostLocal = if (totalBuyQty > BigDecimal.ZERO)
                    totalBuyCost.divide(totalBuyQty, 10, RoundingMode.HALF_UP)
                else BigDecimal.ZERO

                Holding(
                    assetId = key.assetId,
                    assetName = key.assetName,
                    listingId = key.listingId,
                    exchange = key.exchange,
                    ticker = key.ticker,
                    currency = key.currency,
                    quantity = adjustedNetQty,
                    avgCostLocal = avgCostLocal,
                    totalCostLocal = avgCostLocal.multiply(adjustedNetQty).setScale(6, RoundingMode.HALF_UP),
                )
            }
    }

    // Quantity held per (accountId, listingId) — no cost basis, since a transfer carries no price
    // and moving cost basis between accounts would require a lot-carry-over policy that doesn't
    // exist yet. A transfer moves its full quantity out of the source account and, minus its fee
    // (if any), into the destination account. Splits apply uniformly regardless of which account
    // holds the listing.
    fun getHoldingsByAccount(portfolioId: Long, asOf: LocalDate? = null): List<AccountHolding> {
        data class Key(val accountId: Long, val listingId: Long)

        val transactionRows = holdingRepository.findTransactionRows(portfolioId, asOf)
        val transferRows = holdingRepository.findTransferRows(portfolioId, asOf)

        val splitIndex = TransactionReplay.splitIndex(
            transactionRows.filter { it.type == TransactionType.SPLIT }.map { Triple(it.listingId, it.date, it.quantity) },
        )

        val currencyByListingId = mutableMapOf<Long, CurrencyCode>()
        val quantities = mutableMapOf<Key, BigDecimal>()

        transactionRows.filter { it.type != TransactionType.SPLIT }.forEach { r ->
            currencyByListingId.putIfAbsent(r.listingId, r.currency)
            val delta = TransactionReplay.signedQuantityDelta(r.listingId, r.date, r.quantity, r.type, splitIndex)
            quantities.merge(Key(r.accountId, r.listingId), delta, BigDecimal::add)
        }

        transferRows.forEach { t ->
            currencyByListingId.putIfAbsent(t.listingId, t.currency)
            val adjQty = TransactionReplay.splitAdjustedQuantity(t.listingId, t.date, t.quantity, splitIndex)
            val adjFee = t.feeQuantity?.let { TransactionReplay.splitAdjustedQuantity(t.listingId, t.date, it, splitIndex) } ?: BigDecimal.ZERO
            quantities.merge(Key(t.sourceAccountId, t.listingId), -adjQty, BigDecimal::add)
            quantities.merge(Key(t.destinationAccountId, t.listingId), adjQty - adjFee, BigDecimal::add)
        }

        return quantities
            .filterValues { it > BigDecimal.ZERO }
            .map { (key, qty) ->
                AccountHolding(
                    accountId = key.accountId,
                    listingId = key.listingId,
                    currency = currencyByListingId.getValue(key.listingId),
                    quantity = qty,
                )
            }
    }
}
