package com.simpletickr.asset

import com.simpletickr.generated.model.ListingRequest
import com.simpletickr.shared.CurrencyCode
import org.springframework.stereotype.Service

@Service
class CreateListingUseCase(
    private val assetRepository: AssetRepository,
    private val listingRepository: ListingRepository,
) {

    fun execute(assetId: Long, request: ListingRequest): Listing? {
        if (assetRepository.findById(assetId) == null) return null
        return listingRepository.save(assetId, request.exchange, request.ticker, CurrencyCode(request.currency))
    }
}
