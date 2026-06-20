package com.simpletickr.asset

import com.simpletickr.generated.api.AssetsApi
import com.simpletickr.generated.model.CreateAssetRequest
import com.simpletickr.generated.model.ListingRequest
import com.simpletickr.generated.model.UpdateAssetRequest
import com.simpletickr.price.PriceProviderMapping
import com.simpletickr.price.PriceProviderMappingRepository
import com.simpletickr.shared.CurrencyCode
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.Asset as AssetModel
import com.simpletickr.generated.model.AssetDetail as AssetDetailModel
import com.simpletickr.generated.model.AssetType as GeneratedAssetType
import com.simpletickr.generated.model.Listing as ListingModel
import com.simpletickr.generated.model.ListingDetail as ListingDetailModel
import com.simpletickr.generated.model.PriceMapping as PriceMappingModel

@RestController
class AssetController(
    private val assetRepository: AssetRepository,
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
) : AssetsApi {

    override fun listAssets(): ResponseEntity<List<AssetModel>> =
        ResponseEntity.ok(assetRepository.findAll().map { it.toModel() })

    override fun getAsset(id: Long): ResponseEntity<AssetDetailModel> {
        val asset = assetRepository.findById(id) ?: return ResponseEntity.notFound().build()
        val mappings = mappingRepository.findByListingIds(asset.listings.map { it.id })
        return ResponseEntity.ok(asset.toDetailModel(mappings))
    }

    @Transactional
    override fun createAsset(createAssetRequest: CreateAssetRequest): ResponseEntity<AssetModel> {
        if (createAssetRequest.listings.isEmpty()) return ResponseEntity.badRequest().build()
        val saved = assetRepository.save(
            isin = createAssetRequest.isin,
            name = createAssetRequest.name,
            type = AssetType.valueOf(createAssetRequest.type.value),
        )
        for (listingReq in createAssetRequest.listings) {
            val listing = listingRepository.save(saved.id, listingReq.exchange, listingReq.ticker, CurrencyCode(listingReq.currency))
            listingReq.priceMappings?.forEach { m ->
                mappingRepository.upsert(listing.id, m.provider, m.externalId)
            }
        }
        return ResponseEntity.status(201).body(assetRepository.findById(saved.id)!!.toModel())
    }

    override fun updateAsset(id: Long, updateAssetRequest: UpdateAssetRequest): ResponseEntity<AssetModel> {
        assetRepository.update(
            id = id,
            isin = updateAssetRequest.isin,
            name = updateAssetRequest.name,
            type = AssetType.valueOf(updateAssetRequest.type.value),
        ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(assetRepository.findById(id)!!.toModel())
    }

    override fun deleteAsset(id: Long): ResponseEntity<Unit> {
        if (assetRepository.findById(id) == null) return ResponseEntity.notFound().build()
        assetRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    override fun createListing(id: Long, listingRequest: ListingRequest): ResponseEntity<ListingModel> {
        if (assetRepository.findById(id) == null) return ResponseEntity.notFound().build()
        val listing = listingRepository.save(id, listingRequest.exchange, listingRequest.ticker, CurrencyCode(listingRequest.currency))
        return ResponseEntity.status(201).body(listing.toModel())
    }

    override fun updateListing(id: Long, listingRequest: ListingRequest): ResponseEntity<ListingModel> {
        val updated = listingRepository.update(id, listingRequest.exchange, listingRequest.ticker, CurrencyCode(listingRequest.currency))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated.toModel())
    }

    override fun deleteListing(id: Long): ResponseEntity<Unit> {
        if (listingRepository.findById(id) == null) return ResponseEntity.notFound().build()
        listingRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    private fun Asset.toModel() = AssetModel(
        id = id,
        isin = isin,
        name = name,
        type = GeneratedAssetType.valueOf(type.name),
        listings = listings.map { it.toModel() },
    )

    private fun Asset.toDetailModel(mappings: Map<Long, List<PriceProviderMapping>>) = AssetDetailModel(
        id = id,
        isin = isin,
        name = name,
        type = GeneratedAssetType.valueOf(type.name),
        listings = listings.map { l ->
            ListingDetailModel(
                id = l.id,
                assetId = l.assetId,
                exchange = l.exchange,
                ticker = l.ticker,
                currency = l.currency.value,
                priceMappings = (mappings[l.id] ?: emptyList()).map { m ->
                    PriceMappingModel(id = m.id, listingId = m.listingId, provider = m.provider, externalId = m.externalId)
                },
            )
        },
    )

    private fun Listing.toModel() = ListingModel(
        id = id,
        assetId = assetId,
        exchange = exchange,
        ticker = ticker,
        currency = currency.value,
    )
}
