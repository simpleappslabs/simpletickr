package com.simpletickr.asset

import com.simpletickr.price.PriceProviderMapping
import com.simpletickr.price.PriceProviderMappingRepository
import org.springframework.stereotype.Service

data class AssetDetail(val asset: Asset, val mappings: Map<Long, List<PriceProviderMapping>>)

@Service
class AssetService(
    private val assetRepository: AssetRepository,
    private val mappingRepository: PriceProviderMappingRepository,
) {

    fun listAssets(): List<AssetWithPrices> = assetRepository.findAllWithLatestPrice()

    fun getAsset(id: Long): AssetDetail? {
        val asset = assetRepository.findById(id) ?: return null
        val mappings = mappingRepository.findByListingIds(asset.listings.map { it.id })
        return AssetDetail(asset, mappings)
    }
}
