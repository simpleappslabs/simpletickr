package com.simpletickr.transaction

import com.simpletickr.asset.ListingRepository
import com.simpletickr.fx.FxRateService
import com.simpletickr.fx.FxRateSource
import com.simpletickr.settings.UserSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AmendTransactionUseCase(
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
    private val fxRateService: FxRateService,
    private val userSettingsRepository: UserSettingsRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(portfolioId: Long, id: Long, command: AmendTransactionCommand): Transaction? {
        log.info("Amending transaction id={} in portfolio {}", id, portfolioId)
        val existing = transactionRepository.findById(id) ?: return null
        if (existing.portfolioId != portfolioId) return null
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

        return transactionRepository.update(existing.copy(
            listingId = command.listingId,
            assetId = listing.assetId,
            type = command.type,
            quantity = command.quantity,
            price = command.price,
            date = command.date,
            fees = command.fees,
            fxRate = fxRate,
            fxRateSource = fxRateSource,
        ))
    }
}
