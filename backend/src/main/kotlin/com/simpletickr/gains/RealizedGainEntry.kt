package com.simpletickr.gains

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
    // Non-null when this sell was one leg of a crypto-to-crypto swap.
    val tradeId: Long? = null,
    val receivedTicker: String? = null,
    // Which acquisition lot(s) generated this gain — FIFO only. Average-cost has no discrete
    // lots (a single blended pool per asset), so this is always empty for AVCO entries.
    val lots: List<RealizedGainLot> = emptyList(),
)
