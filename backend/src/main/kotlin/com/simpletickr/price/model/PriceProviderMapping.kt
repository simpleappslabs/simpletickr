package com.simpletickr.price.model

data class PriceProviderMapping(
    val id: Long,
    val listingId: Long,
    val provider: String,
    val externalId: String,
)
