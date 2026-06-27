package com.simpletickr.importer

import com.simpletickr.asset.persistence.AssetRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

@Service
class CreateAssetImportMappingUseCase(
    private val assetRepository: AssetRepository,
    private val mappingRepository: AssetImportMappingRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(broker: String, externalName: String, assetId: Long): AssetImportMapping {
        log.info("Creating asset import mapping: broker={}, externalName={}, assetId={}", broker, externalName, assetId)
        assetRepository.findById(assetId)
            ?: throw IllegalArgumentException("Asset $assetId not found")
        return try {
            mappingRepository.save(broker, externalName, assetId)
        } catch (_: DuplicateKeyException) {
            throw IllegalStateException("Mapping already exists for broker=$broker, externalName=$externalName")
        }
    }
}
