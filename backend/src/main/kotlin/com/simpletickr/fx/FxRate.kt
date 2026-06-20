package com.simpletickr.fx

import java.math.BigDecimal
import java.time.LocalDate

data class FxRate(
    val baseCurrency: String,
    val quoteCurrency: String,
    val date: LocalDate,
    val rate: BigDecimal,
    // Interpretation: 1 baseCurrency = rate quoteCurrency
    // To convert local (quote) → base: localAmount / rate
    // To convert base → local (quote): baseAmount * rate
)
