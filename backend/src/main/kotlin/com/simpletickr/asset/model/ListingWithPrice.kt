package com.simpletickr.asset.model

import com.simpletickr.shared.CurrencyCode
import java.math.BigDecimal
import java.time.LocalDate

data class ListingWithPrice(
    val id: Long,
    val assetId: Long,
    val exchange: String?,
    val ticker: String,
    val currency: CurrencyCode,
    val lastPriceDate: LocalDate?,
    val lastPrice: BigDecimal?,
)
