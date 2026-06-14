package com.simpletickr.asset

data class Listing(
    val id: Long,
    val assetId: Long,
    val exchange: String?,
    val ticker: String,
    val currency: String,
)
