package com.simpletickr.price

import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.price.model.PricePoint
import com.simpletickr.price.model.PriceProviderMapping
import com.simpletickr.price.persistence.AssetPriceHistoryRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PriceQueryService(
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
    private val historyRepository: AssetPriceHistoryRepository,
) {

    fun listMappings(listingId: Long): List<PriceProviderMapping>? {
        if (listingRepository.findById(listingId) == null) return null
        return mappingRepository.findByListingId(listingId)
    }

    fun getMapping(listingId: Long, provider: String): PriceProviderMapping? =
        mappingRepository.findByListingAndProvider(listingId, provider)

    fun getPriceHistory(listingId: Long, from: LocalDate, to: LocalDate): List<PricePoint>? {
        if (listingRepository.findById(listingId) == null) return null
        return historyRepository.findByListingId(listingId, from, to)
    }
}
