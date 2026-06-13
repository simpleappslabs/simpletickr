package com.simpletickr.portfolio

import java.math.BigDecimal
import java.time.LocalDate

data class RealizedGainsReport(
    val method: RealizationMethod,
    val from: LocalDate,
    val to: LocalDate,
    val entries: List<RealizedGainEntry>,
    val totalProceeds: BigDecimal,
    val totalBuyFees: BigDecimal,
    val totalSellFees: BigDecimal,
    val totalCostBasis: BigDecimal,
    val totalGain: BigDecimal,
)
