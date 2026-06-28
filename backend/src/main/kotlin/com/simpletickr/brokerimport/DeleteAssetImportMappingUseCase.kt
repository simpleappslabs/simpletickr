package com.simpletickr.brokerimport

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteAssetImportMappingUseCase(private val repository: AssetImportMappingRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long) {
        log.info("Deleting asset import mapping id={}", id)
        repository.delete(id)
    }
}
