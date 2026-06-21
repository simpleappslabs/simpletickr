package com.simpletickr.asset

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CreateListingUseCase(
    private val assetRepository: AssetRepository,
    private val listingRepository: ListingRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(assetId: Long, command: CreateListingCommand): Listing? {
        log.info("Creating listing for asset id={}: {}:{}", assetId, command.exchange, command.ticker)
        if (assetRepository.findById(assetId) == null) return null
        return listingRepository.save(assetId, command.exchange, command.ticker, command.currency)
    }
}
