package com.simpletickr.asset

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdateListingUseCase(private val listingRepository: ListingRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long, command: UpdateListingCommand): Listing? {
        log.info("Updating listing id={}", id)
        return listingRepository.update(id, command.exchange, command.ticker, command.currency)
    }
}
