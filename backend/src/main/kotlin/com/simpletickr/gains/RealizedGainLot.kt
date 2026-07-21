package com.simpletickr.gains

import java.math.BigDecimal
import java.time.LocalDate

// Which acquisition lot (or share of one) contributed to a RealizedGainEntry, and how much.
// costBasis/buyFees are this lot's own share (quantity * pricePerUnit + buyFees), so summing
// costBasis, buyFees, and quantity across every lot on one entry reconciles exactly to that
// entry's own fields — a discrepancy means the calculator has a bug.
data class RealizedGainLot(
    val acquisitionDate: LocalDate,
    val quantity: BigDecimal,
    val pricePerUnit: BigDecimal,
    val buyFees: BigDecimal,
    val costBasis: BigDecimal,
)
