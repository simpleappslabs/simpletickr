package com.simpletickr.transaction

import com.simpletickr.asset.ListingRepository
import org.springframework.stereotype.Service

@Service
class RecordTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
) {

    fun execute(portfolioId: Long, command: RecordTransactionCommand): Transaction {
        val listing = listingRepository.findById(command.listingId)
            ?: throw IllegalArgumentException("Listing ${command.listingId} not found")
        val transaction = Transaction(
            id = 0L,
            portfolioId = portfolioId,
            listingId = command.listingId,
            assetId = listing.assetId,
            type = command.type,
            quantity = command.quantity,
            price = command.price,
            date = command.date,
            fees = command.fees,
        )
        return transactionRepository.save(transaction)
    }
}
