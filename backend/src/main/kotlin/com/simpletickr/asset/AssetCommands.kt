package com.simpletickr.asset

import com.simpletickr.shared.CurrencyCode

data class PriceMappingCommand(val provider: String, val externalId: String)

data class CreateListingCommand(
    val exchange: String?,
    val ticker: String,
    val currency: CurrencyCode,
    val priceMappings: List<PriceMappingCommand>? = null,
)

data class CreateAssetCommand(
    val name: String,
    val type: AssetType,
    val isin: String?,
    val listings: List<CreateListingCommand>,
)

data class UpdateAssetCommand(
    val name: String,
    val type: AssetType,
    val isin: String?,
)

data class UpdateListingCommand(
    val exchange: String?,
    val ticker: String,
    val currency: CurrencyCode,
)
