package com.simpletickr.asset.search

import com.simpletickr.asset.model.AssetType

data class ListingSearchResult(
    val symbol: String,
    val name: String,
    val type: AssetType,
    val exchange: String?,
    val currency: String?,
)
