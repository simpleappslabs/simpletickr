package com.simpletickr.gains

import com.simpletickr.asset.Listing
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.SplitAdjuster
import com.simpletickr.transaction.Transaction
import com.simpletickr.transaction.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object RealizedGainsCalculator {

    fun compute(
        transactions: List<Transaction>,
        listingMap: Map<Long, Listing>,
        method: RealizationMethod,
        from: LocalDate,
        to: LocalDate,
    ): RealizedGainsReport {
        val entries = when (method) {
            RealizationMethod.FIFO -> computeFifo(transactions, listingMap, from, to)
            RealizationMethod.AVERAGE_COST -> computeAverageCost(transactions, listingMap, from, to)
        }
        val byCurrency = entries.groupBy { it.currency }.mapValues { (currency, group) ->
            CurrencyTotal(
                currency = currency,
                tradeCount = group.size,
                totalProceeds = group.sumOf { it.proceeds },
                totalCostBasis = group.sumOf { it.costBasis },
                totalGain = group.sumOf { it.gain },
            )
        }
        return RealizedGainsReport(method = method, from = from, to = to, entries = entries, byCurrency = byCurrency)
    }

    private fun buildSplitIndex(transactions: List<Transaction>): Map<Long, List<Pair<LocalDate, BigDecimal>>> =
        transactions
            .filter { it.type == TransactionType.SPLIT }
            .groupBy { it.assetId }
            .mapValues { (_, splits) -> splits.map { it.date to it.quantity } }

    private fun computeFifo(
        transactions: List<Transaction>,
        listingMap: Map<Long, Listing>,
        from: LocalDate,
        to: LocalDate,
    ): List<RealizedGainEntry> {
        data class Lot(var remaining: BigDecimal, val pricePerUnit: BigDecimal, val feePerUnit: BigDecimal)

        val splitIndex = buildSplitIndex(transactions)
        val lots = mutableMapOf<Long, ArrayDeque<Lot>>()
        val entries = mutableListOf<RealizedGainEntry>()

        for (tx in transactions.sortedWith(compareBy({ it.date }, { it.id }))) {
            val assetLots = lots.getOrPut(tx.assetId) { ArrayDeque() }
            val fees = tx.fees ?: BigDecimal.ZERO

            when (tx.type) {
                TransactionType.BUY -> {
                    val adj = SplitAdjuster.adjustmentFor(tx.assetId, tx.date, splitIndex)
                    val adjQty = tx.quantity * adj.multiplier
                    val adjPrice = if (adj.multiplier == BigDecimal.ONE) tx.price
                                   else tx.price.divide(adj.multiplier, 10, RoundingMode.HALF_UP)
                    val feePerUnit = fees.divide(adjQty, 10, RoundingMode.HALF_UP)
                    assetLots.addLast(Lot(adjQty, adjPrice, feePerUnit))
                }
                TransactionType.SELL -> {
                    var remaining = tx.quantity
                    var totalPurchaseValue = BigDecimal.ZERO
                    var totalBuyFees = BigDecimal.ZERO

                    while (remaining > BigDecimal.ZERO && assetLots.isNotEmpty()) {
                        val lot = assetLots.first()
                        val consumed = remaining.min(lot.remaining)
                        totalPurchaseValue += consumed * lot.pricePerUnit
                        totalBuyFees += consumed * lot.feePerUnit
                        lot.remaining -= consumed
                        remaining -= consumed
                        if (lot.remaining.compareTo(BigDecimal.ZERO) == 0) assetLots.removeFirst()
                    }

                    if (tx.date in from..to) {
                        val listing = listingMap[tx.listingId] ?: continue
                        val costBasis = totalPurchaseValue + totalBuyFees
                        val proceeds = tx.quantity * tx.price - fees
                        entries += RealizedGainEntry(
                            assetId = tx.assetId,
                            ticker = listing.ticker,
                            currency = listing.currency,
                            date = tx.date,
                            quantity = tx.quantity,
                            proceeds = proceeds,
                            buyFees = totalBuyFees,
                            sellFees = fees,
                            costBasis = costBasis,
                            gain = proceeds - costBasis,
                        )
                    }
                }
                TransactionType.SPLIT -> { /* no-op: consumed by splitIndex */ }
            }
        }

        return entries
    }

    private fun computeAverageCost(
        transactions: List<Transaction>,
        listingMap: Map<Long, Listing>,
        from: LocalDate,
        to: LocalDate,
    ): List<RealizedGainEntry> {
        data class AssetState(
            var totalQty: BigDecimal = BigDecimal.ZERO,
            var totalPurchaseValue: BigDecimal = BigDecimal.ZERO,
            var totalBuyFees: BigDecimal = BigDecimal.ZERO,
        )

        val splitIndex = buildSplitIndex(transactions)
        val state = mutableMapOf<Long, AssetState>()
        val entries = mutableListOf<RealizedGainEntry>()

        for (tx in transactions.sortedWith(compareBy({ it.date }, { it.id }))) {
            val s = state.getOrPut(tx.assetId) { AssetState() }
            val fees = tx.fees ?: BigDecimal.ZERO

            when (tx.type) {
                TransactionType.BUY -> {
                    val adj = SplitAdjuster.adjustmentFor(tx.assetId, tx.date, splitIndex)
                    val adjQty = tx.quantity * adj.multiplier
                    val adjPrice = if (adj.multiplier == BigDecimal.ONE) tx.price
                                   else tx.price.divide(adj.multiplier, 10, RoundingMode.HALF_UP)
                    s.totalQty += adjQty
                    s.totalPurchaseValue += adjQty * adjPrice
                    s.totalBuyFees += fees
                }
                TransactionType.SELL -> {
                    if (s.totalQty > BigDecimal.ZERO) {
                        val avgPrice = s.totalPurchaseValue.divide(s.totalQty, 10, RoundingMode.HALF_UP)
                        val avgFee = s.totalBuyFees.divide(s.totalQty, 10, RoundingMode.HALF_UP)
                        val purchaseValue = tx.quantity * avgPrice
                        val allocatedBuyFees = tx.quantity * avgFee
                        val costBasis = purchaseValue + allocatedBuyFees

                        s.totalQty -= tx.quantity
                        s.totalPurchaseValue -= purchaseValue
                        s.totalBuyFees -= allocatedBuyFees

                        if (tx.date in from..to) {
                            val listing = listingMap[tx.listingId] ?: continue
                            val proceeds = tx.quantity * tx.price - fees
                            entries += RealizedGainEntry(
                                assetId = tx.assetId,
                                ticker = listing.ticker,
                                currency = listing.currency,
                                date = tx.date,
                                quantity = tx.quantity,
                                proceeds = proceeds,
                                buyFees = allocatedBuyFees,
                                sellFees = fees,
                                costBasis = costBasis,
                                gain = proceeds - costBasis,
                            )
                        }
                    }
                }
                TransactionType.SPLIT -> { /* no-op: consumed by splitIndex */ }
            }
        }

        return entries
    }
}
