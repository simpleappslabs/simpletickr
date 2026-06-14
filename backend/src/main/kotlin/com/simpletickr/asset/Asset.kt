package com.simpletickr.asset

enum class AssetType {
    STOCK,
    ETF,
    CRYPTO,
    OTHER
}

data class Asset(
    val id: Long,
    val isin: String?,
    val name: String,
    val type: AssetType,
    val listings: List<Listing> = emptyList(),
)
