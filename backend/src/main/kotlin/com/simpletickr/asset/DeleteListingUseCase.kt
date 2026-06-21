package com.simpletickr.asset

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteListingUseCase(private val listingRepository: ListingRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(id: Long): Boolean {
        log.info("Deleting listing id={}", id)
        if (listingRepository.findById(id) == null) return false
        listingRepository.delete(id)
        return true
    }
}
