package com.simpletickr.asset

import com.simpletickr.generated.api.AssetsApi
import com.simpletickr.generated.model.CreateAssetRequest
import com.simpletickr.generated.model.ListingRequest
import com.simpletickr.generated.model.UpdateAssetRequest
import com.simpletickr.price.PriceProviderMapping
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import com.simpletickr.generated.model.Asset as AssetModel
import com.simpletickr.generated.model.AssetDetail as AssetDetailModel
import com.simpletickr.generated.model.AssetType as GeneratedAssetType
import com.simpletickr.generated.model.Listing as ListingModel
import com.simpletickr.generated.model.ListingDetail as ListingDetailModel
import com.simpletickr.generated.model.ListingWithPrice as ListingWithPriceModel
import com.simpletickr.generated.model.PriceMapping as PriceMappingModel

@RestController
class AssetController(
    private val assetService: AssetService,
    private val createAssetUseCase: CreateAssetUseCase,
    private val updateAssetUseCase: UpdateAssetUseCase,
    private val deleteAssetUseCase: DeleteAssetUseCase,
    private val createListingUseCase: CreateListingUseCase,
    private val updateListingUseCase: UpdateListingUseCase,
    private val deleteListingUseCase: DeleteListingUseCase,
) : AssetsApi {

    override fun listAssets(): ResponseEntity<List<AssetModel>> =
        ResponseEntity.ok(assetService.listAssets().map { it.toModel() })

    override fun getAsset(id: Long): ResponseEntity<AssetDetailModel> {
        val detail = assetService.getAsset(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail.asset.toDetailModel(detail.mappings))
    }

    override fun createAsset(createAssetRequest: CreateAssetRequest): ResponseEntity<AssetModel> {
        if (createAssetRequest.listings.isEmpty()) return ResponseEntity.badRequest().build()
        return ResponseEntity.status(201).body(createAssetUseCase.execute(createAssetRequest).toModel())
    }

    override fun updateAsset(id: Long, updateAssetRequest: UpdateAssetRequest): ResponseEntity<AssetModel> {
        val asset = updateAssetUseCase.execute(id, updateAssetRequest) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(asset.toModel())
    }

    override fun deleteAsset(id: Long): ResponseEntity<Unit> {
        if (!deleteAssetUseCase.execute(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    override fun createListing(id: Long, listingRequest: ListingRequest): ResponseEntity<ListingModel> {
        val listing = createListingUseCase.execute(id, listingRequest) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.status(201).body(listing.toModel())
    }

    override fun updateListing(id: Long, listingRequest: ListingRequest): ResponseEntity<ListingModel> {
        val listing = updateListingUseCase.execute(id, listingRequest) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(listing.toModel())
    }

    override fun deleteListing(id: Long): ResponseEntity<Unit> {
        if (!deleteListingUseCase.execute(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    private fun Asset.toModel() = AssetModel(
        id = id,
        isin = isin,
        name = name,
        type = GeneratedAssetType.valueOf(type.name),
        listings = listings.map { l ->
            ListingWithPriceModel(id = l.id, assetId = l.assetId, exchange = l.exchange,
                ticker = l.ticker, currency = l.currency.value, lastPriceDate = null, lastPrice = null)
        },
    )

    private fun AssetWithPrices.toModel() = AssetModel(
        id = id,
        isin = isin,
        name = name,
        type = GeneratedAssetType.valueOf(type.name),
        listings = listings.map { l ->
            ListingWithPriceModel(
                id = l.id,
                assetId = l.assetId,
                exchange = l.exchange,
                ticker = l.ticker,
                currency = l.currency.value,
                lastPriceDate = l.lastPriceDate,
                lastPrice = l.lastPrice?.toDouble(),
            )
        },
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
