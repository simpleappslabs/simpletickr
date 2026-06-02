package com.simpletickr.asset

import com.simpletickr.generated.api.AssetsApi
import com.simpletickr.generated.model.AssetRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import com.simpletickr.generated.model.Asset as AssetModel
import com.simpletickr.generated.model.AssetType as GeneratedAssetType

@RestController
class AssetController(
    private val assetRepository: AssetRepository,
) : AssetsApi {

    override fun listAssets(): ResponseEntity<List<AssetModel>> =
        ResponseEntity.ok(assetRepository.findAll().map { it.toModel() })

    override fun getAsset(id: Long): ResponseEntity<AssetModel> {
        val asset = assetRepository.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(asset.toModel())
    }

    override fun createAsset(assetRequest: AssetRequest): ResponseEntity<AssetModel> {
        val asset = assetRepository.save(
            ticker = assetRequest.ticker,
            name = assetRequest.name,
            type = AssetType.valueOf(assetRequest.type.value),
            currency = assetRequest.currency,
            currentPrice = assetRequest.currentPrice?.let { BigDecimal.valueOf(it) },
        )
        return ResponseEntity.status(201).body(asset.toModel())
    }

    override fun updateAsset(id: Long, assetRequest: AssetRequest): ResponseEntity<AssetModel> {
        val asset = assetRepository.update(
            id = id,
            ticker = assetRequest.ticker,
            name = assetRequest.name,
            type = AssetType.valueOf(assetRequest.type.value),
            currency = assetRequest.currency,
            currentPrice = assetRequest.currentPrice?.let { BigDecimal.valueOf(it) },
        ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(asset.toModel())
    }

    override fun deleteAsset(id: Long): ResponseEntity<Unit> {
        if (assetRepository.findById(id) == null) return ResponseEntity.notFound().build()
        assetRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    private fun Asset.toModel() = AssetModel(
        id = id,
        ticker = ticker,
        name = name,
        type = GeneratedAssetType.valueOf(type.name),
        currency = currency,
        currentPrice = currentPrice?.toDouble(),
    )
}