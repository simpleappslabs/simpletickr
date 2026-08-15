package com.simpletickr.dataexport

import com.fasterxml.jackson.databind.ObjectMapper
import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.dataexport.model.ExistingAccount
import com.simpletickr.dataexport.model.ExistingAsset
import com.simpletickr.dataexport.model.ExistingListing
import com.simpletickr.dataexport.model.ExistingPortfolio
import com.simpletickr.dataexport.model.ExistingState
import com.simpletickr.dataexport.model.ExistingTransaction
import com.simpletickr.dataexport.model.ExistingTransfer
import com.simpletickr.dataexport.model.ImportAnalysis
import com.simpletickr.dataexport.model.ImportPlan
import com.simpletickr.dataexport.model.ImportPlanner
import com.simpletickr.dataexport.model.ImportResult
import com.simpletickr.dataexport.model.SimpletickrExport
import com.simpletickr.portfolio.persistence.PortfolioRepository
import com.simpletickr.price.persistence.PriceProviderMappingRepository
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transfer.Transfer
import com.simpletickr.transfer.TransferRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ImportDataUseCase(
    private val assetRepository: AssetRepository,
    private val accountRepository: AccountRepository,
    private val listingRepository: ListingRepository,
    private val mappingRepository: PriceProviderMappingRepository,
    private val portfolioRepository: PortfolioRepository,
    private val transactionRepository: TransactionRepository,
    private val transferRepository: TransferRepository,
    private val settingsRepository: UserSettingsRepository,
    private val objectMapper: ObjectMapper,
) {

    fun analyze(fileContent: ByteArray, userId: Long): ImportAnalysis {
        val export = parse(fileContent)
            ?: return ImportAnalysis(listOf("Invalid or unreadable JSON"), 0, 0, 0, 0, 0, 0, 0, 0)
        return ImportPlanner.plan(export, fetchExistingState(userId)).toAnalysis()
    }

    @Transactional
    fun apply(fileContent: ByteArray, userId: Long): ImportResult {
        val export = parse(fileContent)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or unreadable JSON")
        val plan = ImportPlanner.plan(export, fetchExistingState(userId))
        if (!plan.isValid)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, plan.errors.joinToString("; "))
        return executePlan(export, plan, userId)
    }

    private fun parse(content: ByteArray): SimpletickrExport? = try {
        objectMapper.readValue(content, SimpletickrExport::class.java)
    } catch (_: Exception) { null }

    // Fetches everything the pure planner needs to decide what's new vs. a duplicate. Transaction
    // and transfer history is only fetched for portfolios the user already has — a fresh import
    // target has no prior history to dedup against by definition.
    private fun fetchExistingState(userId: Long): ExistingState {
        val assets = assetRepository.findAll().map { asset ->
            ExistingAsset(
                id = asset.id,
                uuid = asset.uuid,
                isin = asset.isin,
                listings = asset.listings.map { ExistingListing(it.id, it.ticker, it.exchange) },
            )
        }

        val portfolios = portfolioRepository.findAllForUser(userId)
        val existingPortfolios = portfolios.map { ExistingPortfolio(it.id, it.uuid, it.name) }

        val accounts = accountRepository.findAllForUser(userId).map { ExistingAccount(it.id, it.name) }

        val transactions = portfolios.flatMap { p ->
            transactionRepository.findAllForPortfolio(p.id).map {
                ExistingTransaction(it.portfolioId, it.listingId, it.date, it.type, it.quantity, it.price, it.fees, it.externalId)
            }
        }
        val transfers = portfolios.flatMap { p ->
            transferRepository.findAllForPortfolio(p.id).map {
                ExistingTransfer(
                    it.portfolioId, it.listingId, it.date, it.quantity, it.assetFeeQuantity,
                    it.sourceAccountId, it.destinationAccountId,
                )
            }
        }

        return ExistingState(assets, existingPortfolios, accounts, transactions, transfers)
    }

    private fun executePlan(export: SimpletickrExport, plan: ImportPlan, userId: Long): ImportResult {
        settingsRepository.update(userId, UserSettings(CurrencyCode(export.settings.baseCurrency)))

        // Create assets/listings, trusting the plan's resolution directly — no re-matching here.
        val listingIdMap = mutableMapOf<Long, Long>() // exportListingId -> realListingId
        var assetsCreated = 0
        var listingsCreated = 0

        for (ra in plan.resolvedAssets) {
            val realAssetId = ra.existingId ?: run {
                assetsCreated++
                assetRepository.save(ra.exported.isin, ra.exported.name, AssetType.valueOf(ra.exported.type), ra.exported.uuid).id
            }

            for (rl in ra.resolvedListings) {
                val realListingId = rl.existingId ?: run {
                    listingsCreated++
                    listingRepository.save(realAssetId, rl.exported.exchange, rl.exported.ticker, CurrencyCode(rl.exported.currency)).id
                }
                listingIdMap[rl.exported.id] = realListingId

                for (pm in rl.exported.priceMappings) {
                    if (mappingRepository.findByListingAndProvider(realListingId, pm.provider) == null) {
                        mappingRepository.upsert(realListingId, pm.provider, pm.externalId)
                    }
                }
            }
        }

        var portfoliosCreated = 0
        val portfolioIdMap = mutableMapOf<Long, Long>() // exportPortfolioId -> realPortfolioId
        for (rp in plan.resolvedPortfolios) {
            val realPortfolioId = rp.existingId ?: run {
                portfoliosCreated++
                portfolioRepository.save(rp.exported.name, userId, rp.exported.uuid).id
            }
            portfolioIdMap[rp.exported.id] = realPortfolioId
        }

        var accountsCreated = 0
        val accountIdByName = mutableMapOf<String, Long>()
        for (ra in plan.resolvedAccounts) {
            val realAccountId = ra.existingId ?: run {
                accountsCreated++
                accountRepository.save(Account(
                    id = 0L, userId = userId, name = ra.name, broker = ra.broker,
                    accountType = runCatching { AccountType.valueOf(ra.accountType) }.getOrDefault(AccountType.BROKERAGE),
                    currency = ra.currency, accountNumber = ra.accountNumber, institution = ra.institution,
                )).id
            }
            accountIdByName[ra.name] = realAccountId
        }
        fun accountId(name: String?) = accountIdByName.getValue(name ?: "Default")

        var transactionsImported = 0
        var transfersImported = 0

        for (portfolioPlan in plan.portfolioPlans) {
            val realPortfolioId = portfolioIdMap.getValue(portfolioPlan.exportPortfolioId)

            for (tx in portfolioPlan.transactionsToInsert) {
                val realListingId = listingIdMap[tx.listingId] ?: continue
                val type = runCatching { TransactionType.valueOf(tx.type) }.getOrNull() ?: continue
                transactionRepository.save(Transaction(
                    id = 0,
                    portfolioId = realPortfolioId,
                    listingId = realListingId,
                    assetId = 0, // not used on save
                    type = type,
                    quantity = tx.quantity,
                    price = tx.price,
                    date = tx.date,
                    fees = tx.fees,
                    fxRate = tx.fxRate,
                    fxRateSource = null,
                    externalId = tx.externalId,
                    accountId = accountId(tx.accountName),
                    notes = tx.notes,
                ))
                transactionsImported++
            }

            for (tr in portfolioPlan.transfersToInsert) {
                val realListingId = listingIdMap[tr.listingId] ?: continue
                transferRepository.create(Transfer(
                    id = 0,
                    portfolioId = realPortfolioId,
                    listingId = realListingId,
                    assetId = 0, // not used on create
                    quantity = tr.quantity,
                    assetFeeQuantity = tr.assetFeeQuantity,
                    date = tr.date,
                    sourceAccountId = accountId(tr.sourceAccountName),
                    destinationAccountId = accountId(tr.destinationAccountName),
                    notes = tr.notes,
                ))
                transfersImported++
            }
        }

        return ImportResult(assetsCreated, listingsCreated, portfoliosCreated, transactionsImported, transfersImported, accountsCreated)
    }
}
