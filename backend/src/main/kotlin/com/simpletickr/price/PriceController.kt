package com.simpletickr.price

import com.simpletickr.generated.api.PricesApi
import com.simpletickr.generated.model.PriceMappingRequest
import com.simpletickr.price.model.PricePoint
import com.simpletickr.price.model.PriceProviderMapping
import com.simpletickr.price.usecase.DeletePriceMappingUseCase
import com.simpletickr.price.usecase.SetPriceMappingUseCase
import com.simpletickr.price.usecase.SyncPricesUseCase
import com.simpletickr.sync.SyncTrigger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import com.simpletickr.generated.model.PriceMapping as PriceMappingModel
import com.simpletickr.generated.model.PricePoint as PricePointModel
import com.simpletickr.generated.model.SyncResult as SyncResultModel

@RestController
class PriceController(
    private val priceQueryService: PriceQueryService,
    private val syncPricesUseCase: SyncPricesUseCase,
    private val setPriceMappingUseCase: SetPriceMappingUseCase,
    private val deletePriceMappingUseCase: DeletePriceMappingUseCase,
) : PricesApi {

    override fun listPriceMappings(id: Long): ResponseEntity<List<PriceMappingModel>> {
        val mappings = priceQueryService.listMappings(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mappings.map { it.toModel() })
    }

    override fun getPriceMapping(id: Long, provider: String): ResponseEntity<PriceMappingModel> {
        val mapping = priceQueryService.getMapping(id, provider) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapping.toModel())
    }

    override fun setPriceMapping(id: Long, provider: String, priceMappingRequest: PriceMappingRequest): ResponseEntity<PriceMappingModel> {
        val command = SetPriceMappingCommand(listingId = id, provider = provider, externalId = priceMappingRequest.externalId)
        val mapping = setPriceMappingUseCase.execute(command) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapping.toModel())
    }

    override fun deletePriceMapping(id: Long, provider: String): ResponseEntity<Unit> {
        if (!deletePriceMappingUseCase.execute(id, provider)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    override fun getPriceHistory(from: LocalDate, to: LocalDate, id: Long): ResponseEntity<List<PricePointModel>> {
        val points = priceQueryService.getPriceHistory(id, from, to) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(points.map { it.toModel() })
    }

    override fun syncPrices(from: LocalDate?, to: LocalDate?): ResponseEntity<SyncResultModel> {
        val result = syncPricesUseCase.execute(from, to, SyncTrigger.MANUAL)
        return ResponseEntity.ok(SyncResultModel(synced = result.synced, failed = result.failed))
    }

    override fun syncListingPriceHistory(date: LocalDate, id: Long): ResponseEntity<PricePointModel> {
        val result = syncPricesUseCase.execute(from = date, to = date, trigger = SyncTrigger.MANUAL, listingId = id)
        if (result.synced == 0) return ResponseEntity.notFound().build()
        val points = priceQueryService.getPriceHistory(id, date.minusDays(3), date)
        val point = points?.lastOrNull() ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(point.toModel())
    }

    private fun PriceProviderMapping.toModel() = PriceMappingModel(
        id = id, listingId = listingId, provider = provider, externalId = externalId
    )

    private fun PricePoint.toModel() = PricePointModel(date = date, price = price.toDouble())
}
