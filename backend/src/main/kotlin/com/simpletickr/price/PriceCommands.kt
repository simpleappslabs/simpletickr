package com.simpletickr.price

data class SetPriceMappingCommand(
    val listingId: Long,
    val provider: String,
    val externalId: String,
)
