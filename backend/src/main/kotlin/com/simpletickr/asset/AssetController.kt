package com.simpletickr.asset

import com.simpletickr.generated.api.AssetsApi
import com.simpletickr.generated.model.AssetRequest
import com.simpletickr.generated.model.ListingRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.Asset as AssetModel
import com.simpletickr.generated.model.AssetType as GeneratedAssetType
import com.simpletickr.generated.model.Listing as ListingModel

@RestController
class AssetController(
    private val assetRepository: AssetRepository,
    private val listingRepository: ListingRepository,
) : AssetsApi {

    override fun listAssets(): ResponseEntity<List<AssetModel>> =
        ResponseEntity.ok(assetRepository.findAll().map { it.toModel() })

    override fun getAsset(id: Long): ResponseEntity<AssetModel> {
        val asset = assetRepository.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(asset.toModel())
    }

    override fun createAsset(assetRequest: AssetRequest): ResponseEntity<AssetModel> {
        val listingReq = assetRequest.listing
            ?: return ResponseEntity.badRequest().build()
        val saved = assetRepository.save(
            isin = assetRequest.isin,
            name = assetRequest.name,
            type = AssetType.valueOf(assetRequest.type.value),
        )
        listingRepository.save(saved.id, listingReq.exchange, listingReq.ticker, listingReq.currency)
        return ResponseEntity.status(201).body(assetRepository.findById(saved.id)!!.toModel())
    }

    override fun updateAsset(id: Long, assetRequest: AssetRequest): ResponseEntity<AssetModel> {
        assetRepository.update(
            id = id,
            isin = assetRequest.isin,
            name = assetRequest.name,
            type = AssetType.valueOf(assetRequest.type.value),
        ) ?: return ResponseEntity.notFound().build()
        assetRequest.listing?.let { listingReq ->
            assetRepository.findById(id)?.listings?.firstOrNull()?.let { primary ->
                listingRepository.update(primary.id, listingReq.exchange, listingReq.ticker, listingReq.currency)
            }
        }
        return ResponseEntity.ok(assetRepository.findById(id)!!.toModel())
    }

    override fun deleteAsset(id: Long): ResponseEntity<Unit> {
        if (assetRepository.findById(id) == null) return ResponseEntity.notFound().build()
        assetRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    override fun createListing(id: Long, listingRequest: ListingRequest): ResponseEntity<ListingModel> {
        if (assetRepository.findById(id) == null) return ResponseEntity.notFound().build()
        val listing = listingRepository.save(id, listingRequest.exchange, listingRequest.ticker, listingRequest.currency)
        return ResponseEntity.status(201).body(listing.toModel())
    }

    override fun updateListing(id: Long, listingRequest: ListingRequest): ResponseEntity<ListingModel> {
        val updated = listingRepository.update(id, listingRequest.exchange, listingRequest.ticker, listingRequest.currency)
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

    private fun Listing.toModel() = ListingModel(
        id = id,
        assetId = assetId,
        exchange = exchange,
        ticker = ticker,
        currency = currency,
    )
}
