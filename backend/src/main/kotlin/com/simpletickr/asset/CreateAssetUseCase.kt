package com.simpletickr.asset

import com.simpletickr.generated.model.CreateAssetRequest
import com.simpletickr.price.PriceProviderMappingRepository
import com.simpletickr.shared.CurrencyCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateAssetUseCase(
    private val assetRepository: AssetRepository,
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
) {

    @Transactional
    fun execute(request: CreateAssetRequest): Asset {
        val saved = assetRepository.save(
            isin = request.isin,
            name = request.name,
            type = AssetType.valueOf(request.type.value),
        )
        for (listingReq in request.listings) {
            val listing = listingRepository.save(saved.id, listingReq.exchange, listingReq.ticker, CurrencyCode(listingReq.currency))
            listingReq.priceMappings?.forEach { m ->
                mappingRepository.upsert(listing.id, m.provider, m.externalId)
            }
        }
        return assetRepository.findById(saved.id)!!
    }
}
