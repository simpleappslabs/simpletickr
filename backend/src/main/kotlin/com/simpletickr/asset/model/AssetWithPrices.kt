package com.simpletickr.asset.model

data class AssetWithPrices(
    val id: Long,
    val isin: String?,
    val name: String,
    val type: AssetType,
    val listings: List<ListingWithPrice>,
)
