package com.simpletickr.asset

import com.simpletickr.generated.model.ListingRequest
import com.simpletickr.shared.CurrencyCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CreateListingUseCase(
    private val assetRepository: AssetRepository,
    private val listingRepository: ListingRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(assetId: Long, request: ListingRequest): Listing? {
        log.info("Creating listing for asset id={}: {}:{}", assetId, request.exchange, request.ticker)
        if (assetRepository.findById(assetId) == null) return null
        return listingRepository.save(assetId, request.exchange, request.ticker, CurrencyCode(request.currency))
    }
}
