package com.simpletickr.asset

import org.springframework.stereotype.Service

@Service
class DeleteListingUseCase(private val listingRepository: ListingRepository) {

    fun execute(id: Long): Boolean {
        if (listingRepository.findById(id) == null) return false
        listingRepository.delete(id)
        return true
    }
}
