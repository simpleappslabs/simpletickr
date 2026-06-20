package com.simpletickr.portfolio

import java.math.BigDecimal

// FX and pricing overlay on top of a pure Holding.
// All fields are nullable — valuation requires price data and an FX rate when currencies differ.
data class HoldingWithValuation(
    val holding: Holding,
    val marketValueLocal: BigDecimal?,
    val marketValueBase: BigDecimal?,
    val unrealizedPnlBase: BigDecimal?,
    val unrealizedPnlPct: BigDecimal?,
    val fxUsed: BigDecimal?,
)
