package com.simpletickr.portfolio

import com.simpletickr.shared.CurrencyCode
import java.math.BigDecimal

data class CurrencyTotal(
    val currency: CurrencyCode,
    val tradeCount: Int,
    val totalProceeds: BigDecimal,
    val totalCostBasis: BigDecimal,
    val totalGain: BigDecimal,
)
