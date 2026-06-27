package com.simpletickr.price.usecase

import com.simpletickr.price.persistence.PriceProviderMappingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeletePriceMappingUseCase(private val mappingRepository: PriceProviderMappingRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(listingId: Long, provider: String): Boolean {
        log.info("Deleting price mapping for listing id={}, provider={}", listingId, provider)
        return mappingRepository.delete(listingId, provider)
    }
}
