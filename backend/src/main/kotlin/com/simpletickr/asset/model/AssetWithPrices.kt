package com.simpletickr.asset.model

import java.util.UUID

data class AssetWithPrices(
    val id: Long,
    val uuid: UUID,
    val isin: String?,
    val name: String,
    val type: AssetType,
    val listings: List<ListingWithPrice>,
)
