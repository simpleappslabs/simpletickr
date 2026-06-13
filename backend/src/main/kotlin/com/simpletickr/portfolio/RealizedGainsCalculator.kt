package com.simpletickr.portfolio

import com.simpletickr.asset.Asset
import com.simpletickr.transaction.Transaction
import com.simpletickr.transaction.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object RealizedGainsCalculator {

    fun compute(
        transactions: List<Transaction>,
        assetMap: Map<Long, Asset>,
        method: RealizationMethod,
        from: LocalDate,
        to: LocalDate,
    ): RealizedGainsReport {
        val entries = when (method) {
            RealizationMethod.FIFO -> computeFifo(transactions, assetMap, from, to)
            RealizationMethod.AVERAGE_COST -> computeAverageCost(transactions, assetMap, from, to)
        }
        return RealizedGainsReport(
            method = method,
            from = from,
            to = to,
            entries = entries,
            totalProceeds = entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.proceeds },
            totalBuyFees = entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.buyFees },
            totalSellFees = entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.sellFees },
            totalCostBasis = entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.costBasis },
            totalGain = entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.gain },
        )
    }

    private fun computeFifo(
        transactions: List<Transaction>,
        assetMap: Map<Long, Asset>,
        from: LocalDate,
        to: LocalDate,
    ): List<RealizedGainEntry> {
        data class Lot(var remaining: BigDecimal, val pricePerUnit: BigDecimal, val feePerUnit: BigDecimal)

        val lots = mutableMapOf<Long, ArrayDeque<Lot>>()
        val entries = mutableListOf<RealizedGainEntry>()

        for (tx in transactions.sortedWith(compareBy({ it.date }, { it.id }))) {
            val assetLots = lots.getOrPut(tx.assetId) { ArrayDeque() }
            val fees = tx.fees ?: BigDecimal.ZERO

            when (tx.type) {
                TransactionType.BUY -> {
                    val feePerUnit = fees.divide(tx.quantity, 10, RoundingMode.HALF_UP)
                    assetLots.addLast(Lot(tx.quantity, tx.price, feePerUnit))
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
                        val costBasis = totalPurchaseValue + totalBuyFees
                        val proceeds = tx.quantity * tx.price - fees
                        entries += RealizedGainEntry(
                            assetId = tx.assetId,
                            ticker = assetMap[tx.assetId]?.ticker ?: "?",
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
            }
        }

        return entries
    }

    private fun computeAverageCost(
        transactions: List<Transaction>,
        assetMap: Map<Long, Asset>,
        from: LocalDate,
        to: LocalDate,
    ): List<RealizedGainEntry> {
        data class AssetState(
            var totalQty: BigDecimal = BigDecimal.ZERO,
            var totalPurchaseValue: BigDecimal = BigDecimal.ZERO,
            var totalBuyFees: BigDecimal = BigDecimal.ZERO,
        )

        val state = mutableMapOf<Long, AssetState>()
        val entries = mutableListOf<RealizedGainEntry>()

        for (tx in transactions.sortedWith(compareBy({ it.date }, { it.id }))) {
            val s = state.getOrPut(tx.assetId) { AssetState() }
            val fees = tx.fees ?: BigDecimal.ZERO

            when (tx.type) {
                TransactionType.BUY -> {
                    s.totalQty += tx.quantity
                    s.totalPurchaseValue += tx.quantity * tx.price
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
                            val proceeds = tx.quantity * tx.price - fees
                            entries += RealizedGainEntry(
                                assetId = tx.assetId,
                                ticker = assetMap[tx.assetId]?.ticker ?: "?",
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
            }
        }

        return entries
    }
}
