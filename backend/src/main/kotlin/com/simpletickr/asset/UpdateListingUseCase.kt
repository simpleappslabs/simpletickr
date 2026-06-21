package com.simpletickr.asset

import com.simpletickr.generated.model.ListingRequest
import com.simpletickr.shared.CurrencyCode
import org.springframework.stereotype.Service

@Service
class UpdateListingUseCase(private val listingRepository: ListingRepository) {

    fun execute(id: Long, request: ListingRequest): Listing? =
        listingRepository.update(id, request.exchange, request.ticker, CurrencyCode(request.currency))
}
