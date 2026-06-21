package com.simpletickr.asset

import com.simpletickr.generated.model.UpdateAssetRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdateAssetUseCase(private val assetRepository: AssetRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long, request: UpdateAssetRequest): Asset? {
        log.info("Updating asset id={}", id)
        assetRepository.update(
            id = id,
            isin = request.isin,
            name = request.name,
            type = AssetType.valueOf(request.type.value),
        ) ?: return null
        return assetRepository.findById(id)
    }
}
