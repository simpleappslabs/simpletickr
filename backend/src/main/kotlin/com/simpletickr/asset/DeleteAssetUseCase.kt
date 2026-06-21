package com.simpletickr.asset

import org.springframework.stereotype.Service

@Service
class DeleteAssetUseCase(private val assetRepository: AssetRepository) {

    fun execute(id: Long): Boolean {
        if (assetRepository.findById(id) == null) return false
        assetRepository.delete(id)
        return true
    }
}
