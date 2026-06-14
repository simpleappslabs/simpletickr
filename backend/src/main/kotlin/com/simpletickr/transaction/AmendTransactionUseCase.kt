package com.simpletickr.transaction

import com.simpletickr.asset.ListingRepository
import org.springframework.stereotype.Service

@Service
class AmendTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
) {

    fun execute(portfolioId: Long, id: Long, command: AmendTransactionCommand): Transaction? {
        val existing = transactionRepository.findById(id) ?: return null
        if (existing.portfolioId != portfolioId) return null
        val listing = listingRepository.findById(command.listingId)
            ?: throw IllegalArgumentException("Listing ${command.listingId} not found")
        val amended = existing.copy(
            listingId = command.listingId,
            assetId = listing.assetId,
            type = command.type,
            quantity = command.quantity,
            price = command.price,
            date = command.date,
            fees = command.fees,
        )
        return transactionRepository.update(amended)
    }
}
