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

    fun execute(command: SetPriceMappingCommand): PriceProviderMapping? {
        log.info("Setting price mapping for listing id={}: {}={}", command.listingId, command.provider, command.externalId)
        if (listingRepository.findById(command.listingId) == null) return null
        return mappingRepository.upsert(command.listingId, command.provider, command.externalId)
    }
}
