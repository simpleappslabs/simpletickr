package com.simpletickr.asset

import java.math.BigDecimal

enum class AssetType {
    STOCK,
    ETF,
    CRYPTO,
    OTHER
}

data class Asset(
    val id: Long,
    val ticker: String,
    val name: String,
    val type: AssetType,
    val currency: String,
    val currentPrice: BigDecimal?,
)