package com.simpletickr.dataexport

import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.dataexport.model.AssetExport
import com.simpletickr.dataexport.model.ListingExport
import com.simpletickr.dataexport.model.PortfolioExport
import com.simpletickr.dataexport.model.PriceMappingExport
import com.simpletickr.dataexport.model.SettingsExport
import com.simpletickr.dataexport.model.SimpletickrExport
import com.simpletickr.dataexport.model.TransactionExport
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.transaction.persistence.TransactionRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ExportService(
    private val assetRepository: AssetRepository,
    private val mappingRepository: PriceProviderMappingRepository,
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: UserSettingsRepository,
) {

    fun buildExport(): SimpletickrExport {
        val settings = settingsRepository.find()
        val assets = assetRepository.findAll()
        val allMappings = mappingRepository.findAll().groupBy { it.listingId }
        val portfolios = portfolioRepository.findAll()

        return SimpletickrExport(
            schemaVersion = 1,
            exportedAt = Instant.now(),
            settings = SettingsExport(settings.baseCurrency.value),
            assets = assets.map { asset ->
                AssetExport(
                    id = asset.id,
                    uuid = asset.uuid,
                    isin = asset.isin,
                    name = asset.name,
                    type = asset.type.name,
                    listings = asset.listings.map { listing ->
                        ListingExport(
                            id = listing.id,
                            exchange = listing.exchange,
                            ticker = listing.ticker,
                            currency = listing.currency.value,
                            priceMappings = (allMappings[listing.id] ?: emptyList()).map { m ->
                                PriceMappingExport(m.provider, m.externalId)
                            },
                        )
                    },
                )
            },
            portfolios = portfolios.map { portfolio ->
                PortfolioExport(
                    id = portfolio.id,
                    uuid = portfolio.uuid,
                    name = portfolio.name,
                    transactions = transactionRepository.findAllForPortfolio(portfolio.id).map { tx ->
                        TransactionExport(
                            listingId = tx.listingId,
                            type = tx.type.name,
                            quantity = tx.quantity,
                            price = tx.price,
                            date = tx.date,
                            fees = tx.fees,
                            fxRate = tx.fxRate,
                            externalId = tx.externalId,
                            broker = tx.broker,
                            notes = tx.notes,
                        )
                    },
                )
            },
        )
    }
}
