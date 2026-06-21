package com.simpletickr.price

import com.simpletickr.asset.ListingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SetPriceMappingUseCase(
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(listingId: Long, provider: String, externalId: String): PriceProviderMapping? {
        log.info("Setting price mapping for listing id={}: {}={}", listingId, provider, externalId)
        if (listingRepository.findById(listingId) == null) return null
        return mappingRepository.upsert(listingId, provider, externalId)
    }
}
