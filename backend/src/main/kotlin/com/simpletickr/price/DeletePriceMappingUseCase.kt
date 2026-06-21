package com.simpletickr.price

import org.springframework.stereotype.Service

@Service
class DeletePriceMappingUseCase(private val mappingRepository: PriceProviderMappingRepository) {

    fun execute(listingId: Long, provider: String): Boolean = mappingRepository.delete(listingId, provider)
}
