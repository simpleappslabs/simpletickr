package com.simpletickr.portfolio

import java.math.BigDecimal

data class CurrencyTotal(
    val currency: String,
    val tradeCount: Int,
    val totalProceeds: BigDecimal,
    val totalCostBasis: BigDecimal,
    val totalGain: BigDecimal,
)
