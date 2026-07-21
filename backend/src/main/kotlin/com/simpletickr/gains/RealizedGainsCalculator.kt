package com.simpletickr.gains

import com.simpletickr.asset.model.Listing
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.SplitAdjuster
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object RealizedGainsCalculator {

    // Transactions and transfer fees are two separate chronological streams that both drive the
    // same lot/average-cost replay. This makes their merge explicit instead of an ad hoc local sort.
    sealed interface PortfolioEvent {
        val date: LocalDate
    }

    // A transfer moves custody, not portfolio inventory — it never appears here as a whole
    // event with a price. Only its fee (if any) is a genuine disposal, fed in as one of these.
    data class TransferFeeEvent(
        val transferId: Long,
        val assetId: Long,
        override val date: LocalDate,
        val feeQuantity: BigDecimal,
    ) : PortfolioEvent

    private data class TransactionEvent(val transaction: Transaction) : PortfolioEvent {
        override val date get() = transaction.date
    }

    private fun mergeEvents(transactions: List<Transaction>, transferFees: List<TransferFeeEvent>): List<PortfolioEvent> {
        val events: List<PortfolioEvent> = transactions.map { TransactionEvent(it) } + transferFees
        // Same-day ties: ordinary transactions before transfer fees (transaction.id / transfer.id
        // are separate autoincrement sequences from different tables — never compared directly
        // against each other, only used as a same-kind tiebreak once date/kind already order things).
        return events.sortedWith(compareBy(
            { it.date },
            { when (it) { is TransactionEvent -> 0; is TransferFeeEvent -> 1 } },
            { when (it) { is TransactionEvent -> it.transaction.id; is TransferFeeEvent -> it.transferId } },
        ))
    }

    fun compute(
        transactions: List<Transaction>,
        transferFees: List<TransferFeeEvent>,
        listingMap: Map<Long, Listing>,
        method: RealizationMethod,
        from: LocalDate,
        to: LocalDate,
    ): RealizedGainsReport {
        val entries = when (method) {
            RealizationMethod.FIFO -> computeFifo(transactions, transferFees, listingMap, from, to)
            RealizationMethod.AVERAGE_COST -> computeAverageCost(transactions, transferFees, listingMap, from, to)
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

    // Maps tradeId → ticker of the BUY leg, so SELL entries can reference what was received.
    private fun buildTradeIndex(transactions: List<Transaction>, listingMap: Map<Long, Listing>): Map<Long, String> =
        transactions
            .filter { it.type == TransactionType.BUY && it.tradeId != null }
            .mapNotNull { tx -> tx.tradeId?.let { id -> id to (listingMap[tx.listingId]?.ticker ?: return@mapNotNull null) } }
            .toMap()

    private data class ConsumedLots(val purchaseValue: BigDecimal, val buyFees: BigDecimal, val lots: List<RealizedGainLot>)

    // Pure lot-consumption mechanics — no gain/entry logic. Shared by SELL and transfer-fee
    // handling so a future change to sell-specific semantics (fees, proceeds, gain calc) can't
    // accidentally leak into transfer-fee handling: it calls this and stops (the consumed lots
    // list is simply discarded there — a transfer fee never produces a report entry).
    private fun consumeLotsFifo(
        lots: ArrayDeque<Lot>,
        quantity: BigDecimal,
    ): ConsumedLots {
        var remaining = quantity
        var totalPurchaseValue = BigDecimal.ZERO
        var totalBuyFees = BigDecimal.ZERO
        val consumedLots = mutableListOf<RealizedGainLot>()

        while (remaining > BigDecimal.ZERO && lots.isNotEmpty()) {
            val lot = lots.first()
            val consumed = remaining.min(lot.remaining)
            val consumedValue = consumed * lot.pricePerUnit
            val consumedFees = consumed * lot.feePerUnit
            totalPurchaseValue += consumedValue
            totalBuyFees += consumedFees
            consumedLots += RealizedGainLot(
                acquisitionDate = lot.acquisitionDate,
                quantity = consumed,
                pricePerUnit = lot.pricePerUnit,
                buyFees = consumedFees,
                costBasis = consumedValue + consumedFees,
            )
            lot.remaining -= consumed
            remaining -= consumed
            if (lot.remaining.compareTo(BigDecimal.ZERO) == 0) lots.removeFirst()
        }
        return ConsumedLots(totalPurchaseValue, totalBuyFees, consumedLots)
    }

    private data class Lot(var remaining: BigDecimal, val pricePerUnit: BigDecimal, val feePerUnit: BigDecimal, val acquisitionDate: LocalDate)

    private fun computeFifo(
        transactions: List<Transaction>,
        transferFees: List<TransferFeeEvent>,
        listingMap: Map<Long, Listing>,
        from: LocalDate,
        to: LocalDate,
    ): List<RealizedGainEntry> {
        val splitIndex = buildSplitIndex(transactions)
        val tradeIndex = buildTradeIndex(transactions, listingMap)
        val lots = mutableMapOf<Long, ArrayDeque<Lot>>()
        val entries = mutableListOf<RealizedGainEntry>()

        for (event in mergeEvents(transactions, transferFees)) {
            when (event) {
                is TransactionEvent -> {
                    val tx = event.transaction
                    val assetLots = lots.getOrPut(tx.assetId) { ArrayDeque() }
                    val fees = tx.fees ?: BigDecimal.ZERO

                    when (tx.type) {
                        TransactionType.BUY -> {
                            val adj = SplitAdjuster.adjustmentFor(tx.assetId, tx.date, splitIndex)
                            val adjQty = tx.quantity * adj.multiplier
                            val adjPrice = if (adj.multiplier == BigDecimal.ONE) tx.price
                                           else tx.price.divide(adj.multiplier, 10, RoundingMode.HALF_UP)
                            val feePerUnit = fees.divide(adjQty, 10, RoundingMode.HALF_UP)
                            assetLots.addLast(Lot(adjQty, adjPrice, feePerUnit, tx.date))
                        }
                        TransactionType.SELL -> {
                            val consumed = consumeLotsFifo(assetLots, tx.quantity)
                            if (tx.date in from..to) {
                                val listing = listingMap[tx.listingId] ?: continue
                                val costBasis = consumed.purchaseValue + consumed.buyFees
                                val proceeds = tx.quantity * tx.price - fees
                                entries += RealizedGainEntry(
                                    assetId = tx.assetId,
                                    ticker = listing.ticker,
                                    currency = listing.currency,
                                    date = tx.date,
                                    quantity = tx.quantity,
                                    proceeds = proceeds,
                                    buyFees = consumed.buyFees,
                                    sellFees = fees,
                                    costBasis = costBasis,
                                    gain = proceeds - costBasis,
                                    tradeId = tx.tradeId,
                                    receivedTicker = tx.tradeId?.let { tradeIndex[it] },
                                    lots = consumed.lots,
                                )
                            }
                        }
                        TransactionType.SPLIT -> { /* no-op: consumed by splitIndex */ }
                    }
                }
                is TransferFeeEvent -> {
                    // Reduces the position/lots for subsequent SELLs, but recognizes no disposal.
                    val assetLots = lots.getOrPut(event.assetId) { ArrayDeque() }
                    consumeLotsFifo(assetLots, event.feeQuantity)
                }
            }
        }

        return entries
    }

    private data class AssetState(
        var totalQty: BigDecimal = BigDecimal.ZERO,
        var totalPurchaseValue: BigDecimal = BigDecimal.ZERO,
        var totalBuyFees: BigDecimal = BigDecimal.ZERO,
    )

    private data class ConsumedShare(val purchaseValue: BigDecimal, val buyFees: BigDecimal)

    // Pure average-cost decrement — no gain/entry logic. Shared by SELL and transfer-fee handling
    // for the same reason as consumeLotsFifo above.
    private fun decrementAverageCost(s: AssetState, quantity: BigDecimal): ConsumedShare? {
        if (s.totalQty <= BigDecimal.ZERO) return null
        val avgPrice = s.totalPurchaseValue.divide(s.totalQty, 10, RoundingMode.HALF_UP)
        val avgFee = s.totalBuyFees.divide(s.totalQty, 10, RoundingMode.HALF_UP)
        val purchaseValue = quantity * avgPrice
        val allocatedBuyFees = quantity * avgFee

        s.totalQty -= quantity
        s.totalPurchaseValue -= purchaseValue
        s.totalBuyFees -= allocatedBuyFees

        return ConsumedShare(purchaseValue, allocatedBuyFees)
    }

    private fun computeAverageCost(
        transactions: List<Transaction>,
        transferFees: List<TransferFeeEvent>,
        listingMap: Map<Long, Listing>,
        from: LocalDate,
        to: LocalDate,
    ): List<RealizedGainEntry> {
        val splitIndex = buildSplitIndex(transactions)
        val tradeIndex = buildTradeIndex(transactions, listingMap)
        val state = mutableMapOf<Long, AssetState>()
        val entries = mutableListOf<RealizedGainEntry>()

        for (event in mergeEvents(transactions, transferFees)) {
            when (event) {
                is TransactionEvent -> {
                    val tx = event.transaction
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
                            val consumed = decrementAverageCost(s, tx.quantity)
                            if (consumed != null && tx.date in from..to) {
                                val listing = listingMap[tx.listingId] ?: continue
                                val costBasis = consumed.purchaseValue + consumed.buyFees
                                val proceeds = tx.quantity * tx.price - fees
                                entries += RealizedGainEntry(
                                    assetId = tx.assetId,
                                    ticker = listing.ticker,
                                    currency = listing.currency,
                                    date = tx.date,
                                    quantity = tx.quantity,
                                    proceeds = proceeds,
                                    buyFees = consumed.buyFees,
                                    sellFees = fees,
                                    costBasis = costBasis,
                                    gain = proceeds - costBasis,
                                    tradeId = tx.tradeId,
                                    receivedTicker = tx.tradeId?.let { tradeIndex[it] },
                                )
                            }
                        }
                        TransactionType.SPLIT -> { /* no-op: consumed by splitIndex */ }
                    }
                }
                is TransferFeeEvent -> {
                    val s = state.getOrPut(event.assetId) { AssetState() }
                    decrementAverageCost(s, event.feeQuantity)
                }
            }
        }

        return entries
    }
}
