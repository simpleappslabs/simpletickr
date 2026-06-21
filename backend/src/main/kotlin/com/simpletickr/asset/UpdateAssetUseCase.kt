package com.simpletickr.asset

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdateAssetUseCase(private val assetRepository: AssetRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long, command: UpdateAssetCommand): Asset? {
        log.info("Updating asset id={}", id)
        assetRepository.update(
            id = id,
            isin = command.isin,
            name = command.name,
            type = command.type,
        ) ?: return null
        return assetRepository.findById(id)
    }
}
