package com.simpletickr.dataexport

import com.simpletickr.account.model.Account
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.Asset
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.dataexport.model.AccountExport
import com.simpletickr.dataexport.model.AssetExport
import com.simpletickr.dataexport.model.ListingExport
import com.simpletickr.dataexport.model.PortfolioExport
import com.simpletickr.dataexport.model.PriceMappingExport
import com.simpletickr.dataexport.model.SettingsExport
import com.simpletickr.dataexport.model.SimpletickrExport
import com.simpletickr.dataexport.model.TransactionExport
import com.simpletickr.dataexport.model.TransferExport
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transfer.TransferRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ExportService(
    private val assetRepository: AssetRepository,
    private val accountRepository: AccountRepository,
    private val mappingRepository: PriceProviderMappingRepository,
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val settingsRepository: UserSettingsRepository,
) {

    fun buildExport(portfolioIds: List<Long>? = null): SimpletickrExport {
        val settings = settingsRepository.find()
        val portfolios = if (portfolioIds == null) portfolioRepository.findAll()
            else portfolioRepository.findByIds(portfolioIds.toSet())

        val transactionsByPortfolio = portfolios.associate { it.id to transactionRepository.findAllForPortfolio(it.id) }
        val transfersByPortfolio = portfolios.associate { it.id to transferRepository.findAllForPortfolio(it.id) }

        val allAssets = assetRepository.findAll()
        val allAccounts = accountRepository.findAll()

        val assets: List<Asset>
        val accounts: List<Account>
        if (portfolioIds == null) {
            assets = allAssets
            accounts = allAccounts
        } else {
            val referencedListingIds = (transactionsByPortfolio.values.flatten().map { it.listingId } +
                transfersByPortfolio.values.flatten().map { it.listingId }).toSet()
            assets = allAssets.filter { asset -> asset.listings.any { it.id in referencedListingIds } }

            val referencedAccountIds = (transactionsByPortfolio.values.flatten().map { it.accountId } +
                transfersByPortfolio.values.flatten().flatMap { listOf(it.sourceAccountId, it.destinationAccountId) }).toSet()
            accounts = allAccounts.filter { it.id in referencedAccountIds }
        }

        val allMappings = mappingRepository.findAll().groupBy { it.listingId }
        val accountsById = accounts.associateBy { it.id }

        return SimpletickrExport(
            schemaVersion = 3,
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
            accounts = accounts.map { a ->
                AccountExport(
                    name = a.name,
                    broker = a.broker,
                    accountType = a.accountType.name,
                    currency = a.currency,
                    accountNumber = a.accountNumber,
                    institution = a.institution,
                )
            },
            portfolios = portfolios.map { portfolio ->
                PortfolioExport(
                    id = portfolio.id,
                    uuid = portfolio.uuid,
                    name = portfolio.name,
                    transactions = transactionsByPortfolio.getValue(portfolio.id).map { tx ->
                        TransactionExport(
                            listingId = tx.listingId,
                            type = tx.type.name,
                            quantity = tx.quantity,
                            price = tx.price,
                            date = tx.date,
                            fees = tx.fees,
                            fxRate = tx.fxRate,
                            externalId = tx.externalId,
                            accountName = accountsById[tx.accountId]?.name,
                            notes = tx.notes,
                        )
                    },
                    transfers = transfersByPortfolio.getValue(portfolio.id).map { tr ->
                        TransferExport(
                            listingId = tr.listingId,
                            quantity = tr.quantity,
                            assetFeeQuantity = tr.assetFeeQuantity,
                            date = tr.date,
                            sourceAccountName = accountsById[tr.sourceAccountId]?.name,
                            destinationAccountName = accountsById[tr.destinationAccountId]?.name,
                            notes = tr.notes,
                        )
                    },
                )
            },
        )
    }
}
