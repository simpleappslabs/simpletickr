package com.simpletickr.price

import com.simpletickr.asset.ListingRepository
import org.springframework.stereotype.Service

@Service
class SetPriceMappingUseCase(
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
) {

    fun execute(listingId: Long, provider: String, externalId: String): PriceProviderMapping? {
        if (listingRepository.findById(listingId) == null) return null
        return mappingRepository.upsert(listingId, provider, externalId)
    }
}
