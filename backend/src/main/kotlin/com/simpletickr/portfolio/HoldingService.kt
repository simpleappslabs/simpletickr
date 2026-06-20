package com.simpletickr.portfolio

import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.TransactionType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class HoldingService(private val holdingRepository: HoldingRepository) {

    // WAC holdings per (assetId, listingId). Closed positions (net qty ≤ 0) are excluded.
    fun getHoldings(portfolioId: Long): List<Holding> {
        data class Key(
            val assetId: Long, val assetName: String,
            val listingId: Long, val exchange: String?, val ticker: String, val currency: CurrencyCode,
        )

        return holdingRepository.findTransactionRows(portfolioId)
            .groupBy { Key(it.assetId, it.assetName, it.listingId, it.exchange, it.ticker, it.currency) }
            .mapNotNull { (key, rows) ->
                val netQty = rows.fold(BigDecimal.ZERO) { acc, r ->
                    if (r.type == TransactionType.BUY) acc + r.quantity else acc - r.quantity
                }
                if (netQty <= BigDecimal.ZERO) return@mapNotNull null

                val buys = rows.filter { it.type == TransactionType.BUY }
                val totalBuyQty = buys.sumOf { it.quantity }
                val totalBuyCost = buys.sumOf { it.quantity * it.price }
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
