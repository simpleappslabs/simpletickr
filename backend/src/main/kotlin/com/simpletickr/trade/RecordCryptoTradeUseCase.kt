package com.simpletickr.trade

import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.fx.FxRateService
import com.simpletickr.fx.model.FxRateSource
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.transaction.RecordCryptoTradeCommand
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecordCryptoTradeUseCase(
    private val cryptoTradeRepository: CryptoTradeRepository,
    private val transactionRepository: TransactionRepository,
    private val listingRepository: ListingRepository,
    private val assetRepository: AssetRepository,
    private val fxRateService: FxRateService,
    private val userSettingsRepository: UserSettingsRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(portfolioId: Long, command: RecordCryptoTradeCommand): CryptoTrade {
        log.info("Recording crypto trade: portfolioId={}, sellListing={}, buyListing={}, date={}",
            portfolioId, command.sellListingId, command.buyListingId, command.date)

        require(command.sellListingId != command.buyListingId) {
            "Sell and buy listings must be different"
        }

        val sellListing = listingRepository.findById(command.sellListingId)
            ?: throw IllegalArgumentException("Listing ${command.sellListingId} not found")
        val buyListing = listingRepository.findById(command.buyListingId)
            ?: throw IllegalArgumentException("Listing ${command.buyListingId} not found")

        val sellAsset = assetRepository.findById(sellListing.assetId)
            ?: throw IllegalArgumentException("Asset for listing ${command.sellListingId} not found")
        val buyAsset = assetRepository.findById(buyListing.assetId)
            ?: throw IllegalArgumentException("Asset for listing ${command.buyListingId} not found")

        require(sellAsset.type == AssetType.CRYPTO) {
            "Sell listing must belong to a CRYPTO asset (got ${sellAsset.type})"
        }
        require(buyAsset.type == AssetType.CRYPTO) {
            "Buy listing must belong to a CRYPTO asset (got ${buyAsset.type})"
        }

        val baseCurrency = userSettingsRepository.find().baseCurrency

        val (sellFxRate, sellFxRateSource) = resolveFxRate(baseCurrency, sellListing.currency.value, command.date, fxRateService)
        val (buyFxRate, buyFxRateSource) = resolveFxRate(baseCurrency, buyListing.currency.value, command.date, fxRateService)

        val tradeId = cryptoTradeRepository.create(portfolioId)

        val sellTx = transactionRepository.save(Transaction(
            id = 0L,
            portfolioId = portfolioId,
            listingId = command.sellListingId,
            assetId = sellListing.assetId,
            type = TransactionType.SELL,
            quantity = command.sellQuantity,
            price = command.sellPrice,
            date = command.date,
            fees = command.fees,
            fxRate = sellFxRate,
            fxRateSource = sellFxRateSource,
            broker = command.broker,
            notes = command.notes,
            tradeId = tradeId,
        ))

        val buyTx = transactionRepository.save(Transaction(
            id = 0L,
            portfolioId = portfolioId,
            listingId = command.buyListingId,
            assetId = buyListing.assetId,
            type = TransactionType.BUY,
            quantity = command.buyQuantity,
            price = command.buyPrice,
            date = command.date,
            fees = null,
            fxRate = buyFxRate,
            fxRateSource = buyFxRateSource,
            broker = command.broker,
            notes = command.notes,
            tradeId = tradeId,
        ))

        return CryptoTrade(id = tradeId, portfolioId = portfolioId, sell = sellTx, buy = buyTx)
    }

    private fun resolveFxRate(
        baseCurrency: com.simpletickr.shared.CurrencyCode,
        listingCurrency: String,
        date: java.time.LocalDate,
        fxRateService: FxRateService,
    ): Pair<java.math.BigDecimal?, FxRateSource?> {
        val listingCurrencyCode = com.simpletickr.shared.CurrencyCode(listingCurrency)
        if (listingCurrencyCode == baseCurrency) return null to null
        val found = fxRateService.lookupOrFetch(baseCurrency, listingCurrencyCode, date)
            ?: throw IllegalArgumentException(
                "No FX rate available for ${baseCurrency}/${listingCurrency} on $date. " +
                "Please provide the rate manually."
            )
        return found.rate to FxRateSource.AUTO
    }
}
