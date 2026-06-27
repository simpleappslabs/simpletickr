package com.simpletickr.portfolio.model

import java.math.BigDecimal
import java.time.LocalDate

data class PortfolioValuePoint(
    val date: LocalDate,
    val value: BigDecimal?,
    val invested: BigDecimal?,
)
