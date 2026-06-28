package com.simpletickr.asset.model

import java.util.UUID

enum class AssetType {
    STOCK,
    ETF,
    CRYPTO,
    OTHER
}

data class Asset(
    val id: Long,
    val uuid: UUID,
    val isin: String?,
    val name: String,
    val type: AssetType,
    val listings: List<Listing> = emptyList(),
)
