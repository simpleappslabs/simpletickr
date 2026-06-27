package com.simpletickr.asset.usecase

import com.simpletickr.asset.persistence.AssetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteAssetUseCase(private val assetRepository: AssetRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long): Boolean {
        log.info("Deleting asset id={}", id)
        if (assetRepository.findById(id) == null) return false
        assetRepository.delete(id)
        return true
    }
}
