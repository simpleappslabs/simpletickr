package com.simpletickr.portfolio

import com.simpletickr.portfolio.model.Holding
import com.simpletickr.portfolio.persistence.HoldingRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.SplitAdjuster
import com.simpletickr.transaction.model.TransactionType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class HoldingService(private val holdingRepository: HoldingRepository) {

    // WAC holdings per (assetId, listingId). Closed positions (net qty ≤ 0) are excluded.
    // Split transactions are consumed to adjust prior BUY/SELL quantities and prices.
    fun getHoldings(portfolioId: Long): List<Holding> {
        data class Key(
            val assetId: Long, val assetName: String,
            val listingId: Long, val exchange: String?, val ticker: String, val currency: CurrencyCode,
        )

        return holdingRepository.findTransactionRows(portfolioId)
            .groupBy { Key(it.assetId, it.assetName, it.listingId, it.exchange, it.ticker, it.currency) }
            .mapNotNull { (key, rows) ->
                val splitIndex = rows
                    .filter { it.type == TransactionType.SPLIT }
                    .groupBy { it.listingId }
                    .mapValues { (_, splits) -> splits.map { it.date to it.quantity } }

                val regulars = rows.filter { it.type != TransactionType.SPLIT }

                val netQty = regulars.fold(BigDecimal.ZERO) { acc, r ->
                    val adj = SplitAdjuster.adjustmentFor(r.listingId, r.date, splitIndex)
                    val adjQty = r.quantity * adj.multiplier
                    if (r.type == TransactionType.BUY) acc + adjQty else acc - adjQty
                }
                if (netQty <= BigDecimal.ZERO) return@mapNotNull null

                val buys = regulars.filter { it.type == TransactionType.BUY }
                val totalBuyQty = buys.sumOf { r ->
                    r.quantity * SplitAdjuster.adjustmentFor(r.listingId, r.date, splitIndex).multiplier
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
                    quantity = netQty,
                    avgCostLocal = avgCostLocal,
                    totalCostLocal = avgCostLocal.multiply(netQty).setScale(6, RoundingMode.HALF_UP),
                )
            }
    }
}
