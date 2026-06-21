package com.simpletickr.asset

import com.simpletickr.generated.model.ListingRequest
import com.simpletickr.shared.CurrencyCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdateListingUseCase(private val listingRepository: ListingRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long, request: ListingRequest): Listing? {
        log.info("Updating listing id={}", id)
        return listingRepository.update(id, request.exchange, request.ticker, CurrencyCode(request.currency))
    }
}
