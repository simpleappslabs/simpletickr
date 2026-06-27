package com.simpletickr.asset

data class ListingSearchResult(
    val symbol: String,
    val name: String,
    val type: AssetType,
    val exchange: String?,
    val currency: String?,
)
