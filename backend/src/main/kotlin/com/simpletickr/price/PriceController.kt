package com.simpletickr.price

import com.simpletickr.asset.ListingRepository
import com.simpletickr.generated.api.PricesApi
import com.simpletickr.generated.model.PriceMappingRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import com.simpletickr.generated.model.PriceMapping as PriceMappingModel
import com.simpletickr.generated.model.PricePoint as PricePointModel
import com.simpletickr.generated.model.SyncResult as SyncResultModel

@RestController
class PriceController(
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
    private val historyRepository: AssetPriceHistoryRepository,
    private val priceService: PriceService,
) : PricesApi {

    override fun getPriceMapping(id: Long, provider: String): ResponseEntity<PriceMappingModel> {
        val mapping = mappingRepository.findByListingAndProvider(id, provider)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapping.toModel())
    }

    override fun setPriceMapping(id: Long, provider: String, priceMappingRequest: PriceMappingRequest): ResponseEntity<PriceMappingModel> {
        if (listingRepository.findById(id) == null) return ResponseEntity.notFound().build()
        val mapping = mappingRepository.upsert(id, provider, priceMappingRequest.externalId)
        return ResponseEntity.ok(mapping.toModel())
    }

    override fun deletePriceMapping(id: Long, provider: String): ResponseEntity<Unit> {
        if (!mappingRepository.delete(id, provider)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    override fun getPriceHistory(from: LocalDate, to: LocalDate, id: Long): ResponseEntity<List<PricePointModel>> {
        if (listingRepository.findById(id) == null) return ResponseEntity.notFound().build()
        val points = historyRepository.findByListingId(id, from, to).map { it.toModel() }
        return ResponseEntity.ok(points)
    }

    override fun syncPrices(): ResponseEntity<SyncResultModel> {
        val result = priceService.syncAll()
        return ResponseEntity.ok(SyncResultModel(synced = result.synced, failed = result.failed))
    }

    private fun PriceProviderMapping.toModel() = PriceMappingModel(
        id = id, listingId = listingId, provider = provider, externalId = externalId
    )

    private fun PricePoint.toModel() = PricePointModel(date = date, price = price.toDouble())
}
