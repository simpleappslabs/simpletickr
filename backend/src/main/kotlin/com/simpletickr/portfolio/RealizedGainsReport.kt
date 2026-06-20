package com.simpletickr.portfolio

import com.simpletickr.shared.CurrencyCode
import java.time.LocalDate

data class RealizedGainsReport(
    val method: RealizationMethod,
    val from: LocalDate,
    val to: LocalDate,
    val entries: List<RealizedGainEntry>,
    // Per-currency totals — safe to display independently. Never sum across currencies.
    val byCurrency: Map<CurrencyCode, CurrencyTotal>,
)
