package com.simpletickr.importer

import org.springframework.stereotype.Service

@Service
class AssetImportMappingService(private val repository: AssetImportMappingRepository) {

    fun listMappings(broker: String? = null): List<AssetImportMapping> =
        repository.findAll(broker)
}
