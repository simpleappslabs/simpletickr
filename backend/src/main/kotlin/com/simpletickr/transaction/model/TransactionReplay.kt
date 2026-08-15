package com.simpletickr.transaction.model

import java.math.BigDecimal
import java.time.LocalDate

// Split-adjusted quantity deltas, shared by every place that replays a transaction/transfer
// history into a net position: HoldingService.getHoldings, HoldingService.getHoldingsByAccount,
// and PortfolioValueHistoryCalculator. A row's split adjustment is always relative to its own
// date — "product of every split after this row" — never to the date it's being evaluated at,
// so a transaction and a transfer fee on the same day always get the same treatment.
object TransactionReplay {

    fun splitIndex(splits: List<Triple<Long, LocalDate, BigDecimal>>): Map<Long, List<Pair<LocalDate, BigDecimal>>> =
        splits.groupBy { it.first }.mapValues { (_, rows) -> rows.map { it.second to it.third } }

    // Signed, split-adjusted quantity contributed by a single BUY/SELL row.
    fun signedQuantityDelta(
        listingId: Long,
        date: LocalDate,
        quantity: BigDecimal,
        type: TransactionType,
        splitIndex: Map<Long, List<Pair<LocalDate, BigDecimal>>>,
    ): BigDecimal {
        require(type == TransactionType.BUY || type == TransactionType.SELL) {
            "signedQuantityDelta only applies to BUY/SELL rows, got $type"
        }
        val adjQty = splitAdjustedQuantity(listingId, date, quantity, splitIndex)
        return if (type == TransactionType.BUY) adjQty else -adjQty
    }

    // Split-adjusted magnitude of a quantity recorded on `date` — used directly for transfer
    // fees, which reduce quantity but aren't BUY/SELL rows themselves.
    fun splitAdjustedQuantity(
        listingId: Long,
        date: LocalDate,
        quantity: BigDecimal,
        splitIndex: Map<Long, List<Pair<LocalDate, BigDecimal>>>,
    ): BigDecimal = quantity * SplitAdjuster.adjustmentFor(listingId, date, splitIndex).multiplier
}
