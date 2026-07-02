package com.simpletickr.transaction.usecase

import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.fx.FxRateService
import com.simpletickr.fx.model.FxRateSource
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.transaction.RecordTransactionCommand
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RecordTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
    private val fxRateService: FxRateService,
    private val userSettingsRepository: UserSettingsRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(portfolioId: Long, command: RecordTransactionCommand): Transaction {
        log.info("Recording {} transaction: portfolioId={}, listingId={}, date={}", command.type, portfolioId, command.listingId, command.date)
        val listing = listingRepository.findById(command.listingId)
            ?: throw IllegalArgumentException("Listing ${command.listingId} not found")
        val baseCurrency = userSettingsRepository.find().baseCurrency

        val (fxRate, fxRateSource) = when {
            command.type == TransactionType.SPLIT -> null to null
            listing.currency == baseCurrency -> null to null
            command.fxRate != null -> command.fxRate to FxRateSource.USER
            else -> {
                val found = fxRateService.lookupOrFetch(baseCurrency, listing.currency, command.date)
                    ?: throw IllegalArgumentException(
                        "No FX rate available for ${baseCurrency}/${listing.currency} on ${command.date}. " +
                        "Please provide the rate manually."
                    )
                found.rate to FxRateSource.AUTO
            }
        }

        return transactionRepository.save(Transaction(
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
            fxRateSource = fxRateSource,
            externalId = command.externalId,
            broker = command.broker,
            notes = command.notes,
        ))
    }
}
