package com.simpletickr.portfolio

import com.simpletickr.shared.CurrencyCode
import java.math.BigDecimal
import java.time.LocalDate

data class RealizedGainEntry(
    val assetId: Long,
    val ticker: String,
    val currency: CurrencyCode,
    val date: LocalDate,
    val quantity: BigDecimal,
    val proceeds: BigDecimal,
    val buyFees: BigDecimal,
    val sellFees: BigDecimal,
    val costBasis: BigDecimal,
    val gain: BigDecimal,
)
