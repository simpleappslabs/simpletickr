package com.simpletickr.asset

import com.simpletickr.shared.CurrencyCode

data class Listing(
    val id: Long,
    val assetId: Long,
    val exchange: String?,
    val ticker: String,
    val currency: CurrencyCode,
)
