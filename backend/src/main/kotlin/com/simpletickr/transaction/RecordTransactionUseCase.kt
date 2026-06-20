package com.simpletickr.transaction

import com.simpletickr.asset.ListingRepository
import com.simpletickr.fx.FxRateRepository
import com.simpletickr.settings.UserSettingsRepository
import org.springframework.stereotype.Service

@Service
class RecordTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
    private val fxRateRepository: FxRateRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {

    fun execute(portfolioId: Long, command: RecordTransactionCommand): Transaction {
        val listing = listingRepository.findById(command.listingId)
            ?: throw IllegalArgumentException("Listing ${command.listingId} not found")
        val baseCurrency = userSettingsRepository.find().baseCurrency
        val fxRate = if (listing.currency != baseCurrency)
            fxRateRepository.findOnDate(baseCurrency, listing.currency, command.date)
        else null

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
            fxRate = fxRate,
        )
        return transactionRepository.save(transaction)
    }
}
