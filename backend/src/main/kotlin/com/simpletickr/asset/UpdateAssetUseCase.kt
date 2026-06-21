package com.simpletickr.asset

import com.simpletickr.generated.model.UpdateAssetRequest
import org.springframework.stereotype.Service

@Service
class UpdateAssetUseCase(private val assetRepository: AssetRepository) {

    fun execute(id: Long, request: UpdateAssetRequest): Asset? {
        assetRepository.update(
            id = id,
            isin = request.isin,
            name = request.name,
            type = AssetType.valueOf(request.type.value),
        ) ?: return null
        return assetRepository.findById(id)
    }
}
