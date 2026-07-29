package com.simpletickr.dataexport

import com.fasterxml.jackson.databind.ObjectMapper
import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.dataexport.model.AssetExport
import com.simpletickr.dataexport.model.ImportAnalysis
import com.simpletickr.dataexport.model.ImportResult
import com.simpletickr.dataexport.model.ListingExport
import com.simpletickr.dataexport.model.PortfolioExport
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
import java.util.UUID

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

    private data class ResolvedAsset(
        val exported: AssetExport,
        val existingId: Long?,
        val resolvedListings: List<ResolvedListing>,
    ) {
        val needsCreate get() = existingId == null
    }

    private data class ResolvedListing(
        val exported: ListingExport,
        val existingId: Long?,
    ) {
        val needsCreate get() = existingId == null
    }

    private data class ResolvedPortfolio(
        val exported: PortfolioExport,
        val existingId: Long?,
    ) {
        val needsCreate get() = existingId == null
    }

    private data class ImportPlan(
        val errors: List<String>,
        val resolvedAssets: List<ResolvedAsset>,
        val resolvedPortfolios: List<ResolvedPortfolio>,
        val transactionsToInsert: Int,
        val transactionsSkipped: Int,
        val transfersToInsert: Int = 0,
        val transfersSkipped: Int = 0,
    ) {
        val isValid get() = errors.isEmpty()

        fun toAnalysis() = ImportAnalysis(
            errors = errors,
            assetsToCreate = resolvedAssets.count { it.needsCreate },
            assetsExisting = resolvedAssets.count { !it.needsCreate },
            listingsToCreate = resolvedAssets.sumOf { a -> a.resolvedListings.count { it.needsCreate } },
            listingsExisting = resolvedAssets.sumOf { a -> a.resolvedListings.count { !it.needsCreate } },
            portfoliosToCreate = resolvedPortfolios.count { it.needsCreate },
            portfoliosExisting = resolvedPortfolios.count { !it.needsCreate },
            transactionsToImport = transactionsToInsert,
            transactionsSkipped = transactionsSkipped,
            transfersToImport = transfersToInsert,
            transfersSkipped = transfersSkipped,
        )
    }

    fun analyze(fileContent: ByteArray, userId: Long): ImportAnalysis {
        val export = parse(fileContent)
            ?: return ImportAnalysis(listOf("Invalid or unreadable JSON"), 0, 0, 0, 0, 0, 0, 0, 0)
        return buildPlan(export, userId).toAnalysis()
    }

    @Transactional
    fun apply(fileContent: ByteArray, userId: Long): ImportResult {
        val export = parse(fileContent)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or unreadable JSON")
        val plan = buildPlan(export, userId)
        if (!plan.isValid)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, plan.errors.joinToString("; "))
        return executePlan(export, plan, userId)
    }

    private fun parse(content: ByteArray): SimpletickrExport? = try {
        objectMapper.readValue(content, SimpletickrExport::class.java)
    } catch (_: Exception) { null }

    private fun buildPlan(export: SimpletickrExport, userId: Long): ImportPlan {
        val errors = mutableListOf<String>()

        if (export.schemaVersion !in 1..3) {
            errors.add("Unsupported schema version: ${export.schemaVersion}. Supported versions: 1, 2, 3.")
            return ImportPlan(errors, emptyList(), emptyList(), 0, 0)
        }

        // Validate referential integrity within the file
        val exportListingIds = export.assets.flatMap { it.listings }.map { it.id }.toSet()

        val dupAssetIds = export.assets.map { it.id }.groupBy { it }.filter { it.value.size > 1 }.keys
        if (dupAssetIds.isNotEmpty()) errors.add("Duplicate asset IDs in export: $dupAssetIds")

        val dupListingIds = export.assets.flatMap { it.listings }.map { it.id }
            .groupBy { it }.filter { it.value.size > 1 }.keys
        if (dupListingIds.isNotEmpty()) errors.add("Duplicate listing IDs in export: $dupListingIds")

        val dupPortfolioIds = export.portfolios.map { it.id }.groupBy { it }.filter { it.value.size > 1 }.keys
        if (dupPortfolioIds.isNotEmpty()) errors.add("Duplicate portfolio IDs in export: $dupPortfolioIds")

        val badListingRefs = export.portfolios.flatMap { it.transactions }
            .map { it.listingId }.filter { it !in exportListingIds }.toSet()
        if (badListingRefs.isNotEmpty())
            errors.add("Transactions reference listing IDs not present in export: $badListingRefs")

        val badTransferListingRefs = export.portfolios.flatMap { it.transfers }
            .map { it.listingId }.filter { it !in exportListingIds }.toSet()
        if (badTransferListingRefs.isNotEmpty())
            errors.add("Transfers reference listing IDs not present in export: $badTransferListingRefs")

        if (errors.isNotEmpty()) return ImportPlan(errors, emptyList(), emptyList(), 0, 0)

        // Match assets
        val existingAssets = assetRepository.findAll()
        val existingByUuid = existingAssets.associateBy { it.uuid }
        val existingByIsin = existingAssets.filter { it.isin != null }.groupBy { it.isin!! }
        val existingByListingKey = existingAssets.flatMap { asset ->
            asset.listings.map { listing -> "${listing.ticker}|${listing.exchange}" to asset }
        }.toMap()

        val resolvedAssets = export.assets.map { exportAsset ->
            val exportListingKeys = exportAsset.listings.map { "${it.ticker}|${it.exchange}" }.toSet()
            val existing = existingByUuid[exportAsset.uuid]
                ?: exportAsset.isin?.let { isin ->
                    val matches = existingByIsin[isin] ?: emptyList()
                    when {
                        matches.size > 1 -> {
                            errors.add("Ambiguous ISIN match for asset '${exportAsset.name}' (ISIN=${isin}): ${matches.size} existing assets match.")
                            null
                        }
                        else -> matches.firstOrNull()
                    }
                }
                ?: run {
                    val matchedAssets = exportListingKeys.mapNotNull { existingByListingKey[it] }.distinct()
                    when {
                        matchedAssets.size > 1 -> {
                            errors.add("Ambiguous listing match for asset '${exportAsset.name}': ${matchedAssets.size} existing assets match.")
                            null
                        }
                        else -> matchedAssets.firstOrNull()
                    }
                }

            val resolvedListings = if (existing != null) {
                val existingListingsByKey = existing.listings.associateBy { "${it.ticker}|${it.exchange}" }
                exportAsset.listings.map { exportListing ->
                    val key = "${exportListing.ticker}|${exportListing.exchange}"
                    ResolvedListing(exportListing, existingListingsByKey[key]?.id)
                }
            } else {
                exportAsset.listings.map { ResolvedListing(it, null) }
            }

            ResolvedAsset(exportAsset, existing?.id, resolvedListings)
        }

        // Build export listing ID → real listing ID map (only for listings that already exist)
        val existingListingMap: Map<Long, Long> = resolvedAssets
            .filter { !it.needsCreate }
            .flatMap { asset ->
                asset.resolvedListings.mapNotNull { l ->
                    l.existingId?.let { l.exported.id to it }
                }
            }.toMap()

        // Match portfolios (only within the importing user's own portfolios)
        val existingPortfolios = portfolioRepository.findAllForUser(userId)
        val existingPortfoliosByUuid = existingPortfolios.associateBy { it.uuid }
        val existingPortfoliosByName = existingPortfolios.associateBy { it.name }

        val resolvedPortfolios = export.portfolios.map { exportPortfolio ->
            val existing = existingPortfoliosByUuid[exportPortfolio.uuid]
                ?: existingPortfoliosByName[exportPortfolio.name]
            ResolvedPortfolio(exportPortfolio, existing?.id)
        }

        // Count transactions: dedup only when both portfolio and listing already exist
        var toInsert = 0
        var skipped = 0

        for (rp in resolvedPortfolios) {
            val realPortfolioId = rp.existingId
            for (tx in rp.exported.transactions) {
                val realListingId = existingListingMap[tx.listingId]
                if (realPortfolioId != null && realListingId != null) {
                    val type = runCatching { TransactionType.valueOf(tx.type) }.getOrNull()
                    if (type != null && transactionRepository.existsIdentical(
                            realPortfolioId, realListingId, tx.date, type,
                            tx.quantity, tx.price, tx.fees, tx.externalId
                        )
                    ) {
                        skipped++
                    } else {
                        toInsert++
                    }
                } else {
                    toInsert++
                }
            }
        }

        // Count transfers: dedup only when portfolio, listing and both accounts already exist
        val existingAccountsByName = accountRepository.findAllForUser(userId).associateBy { it.name }
        var transfersToInsert = 0
        var transfersSkipped = 0

        for (rp in resolvedPortfolios) {
            val realPortfolioId = rp.existingId
            for (tr in rp.exported.transfers) {
                val realListingId = existingListingMap[tr.listingId]
                val realSourceAccountId = existingAccountsByName[tr.sourceAccountName ?: "Default"]?.id
                val realDestinationAccountId = existingAccountsByName[tr.destinationAccountName ?: "Default"]?.id
                if (realPortfolioId != null && realListingId != null &&
                    realSourceAccountId != null && realDestinationAccountId != null &&
                    transferRepository.existsIdentical(
                        realPortfolioId, realListingId, tr.date,
                        tr.quantity, tr.assetFeeQuantity,
                        realSourceAccountId, realDestinationAccountId,
                    )
                ) {
                    transfersSkipped++
                } else {
                    transfersToInsert++
                }
            }
        }

        return ImportPlan(errors, resolvedAssets, resolvedPortfolios, toInsert, skipped, transfersToInsert, transfersSkipped)
    }

    private fun executePlan(export: SimpletickrExport, plan: ImportPlan, userId: Long): ImportResult {
        // Apply settings
        settingsRepository.update(userId, UserSettings(CurrencyCode(export.settings.baseCurrency)))

        // Create assets and build final listing ID map
        val listingIdMap = mutableMapOf<Long, Long>() // exportListingId → realListingId
        var assetsCreated = 0
        var listingsCreated = 0

        for (ra in plan.resolvedAssets) {
            val realAssetId: Long = if (ra.needsCreate) {
                val asset = assetRepository.save(ra.exported.isin, ra.exported.name, AssetType.valueOf(ra.exported.type), ra.exported.uuid)
                assetsCreated++
                asset.id
            } else {
                ra.existingId!!
            }

            // Resolve listings for this asset
            val existingListings = if (!ra.needsCreate) {
                listingRepository.findByAssetId(realAssetId)
            } else emptyList()
            val existingByKey = existingListings.associateBy { "${it.ticker}|${it.exchange}" }

            for (rl in ra.resolvedListings) {
                val realListingId: Long = if (rl.needsCreate) {
                    val listing = listingRepository.save(
                        realAssetId, rl.exported.exchange, rl.exported.ticker,
                        CurrencyCode(rl.exported.currency)
                    )
                    listingsCreated++
                    listing.id
                } else {
                    val key = "${rl.exported.ticker}|${rl.exported.exchange}"
                    existingByKey[key]?.id ?: rl.existingId!!
                }
                listingIdMap[rl.exported.id] = realListingId

                // Upsert price mappings (skip if already mapped for this provider)
                for (pm in rl.exported.priceMappings) {
                    if (mappingRepository.findByListingAndProvider(realListingId, pm.provider) == null) {
                        mappingRepository.upsert(realListingId, pm.provider, pm.externalId)
                    }
                }
            }
        }

        // Create portfolios
        var portfoliosCreated = 0
        val portfolioIdMap = mutableMapOf<Long, Long>() // exportPortfolioId → realPortfolioId

        for (rp in plan.resolvedPortfolios) {
            val realPortfolioId: Long = if (rp.needsCreate) {
                portfolioRepository.save(rp.exported.name, userId, rp.exported.uuid).id.also { portfoliosCreated++ }
            } else {
                rp.existingId!!
            }
            portfolioIdMap[rp.exported.id] = realPortfolioId
        }

        // Resolve accounts: build name → id map, create missing ones
        val existingAccounts = accountRepository.findAllForUser(userId).associateBy { it.name }.toMutableMap()
        fun resolveAccount(name: String?): Long {
            val key = name ?: "Default"
            return existingAccounts.getOrPut(key) {
                accountRepository.save(Account(
                    id = 0L, userId = userId, name = key, broker = null,
                    accountType = AccountType.BROKERAGE,
                    currency = null, accountNumber = null, institution = null,
                ))
            }.id
        }

        // Insert transactions (dedup)
        var transactionsImported = 0

        for (rp in plan.resolvedPortfolios) {
            val realPortfolioId = portfolioIdMap[rp.exported.id] ?: continue
            for (tx in rp.exported.transactions) {
                val realListingId = listingIdMap[tx.listingId] ?: continue
                val type = runCatching { TransactionType.valueOf(tx.type) }.getOrNull() ?: continue
                if (!transactionRepository.existsIdentical(
                        realPortfolioId, realListingId, tx.date, type,
                        tx.quantity, tx.price, tx.fees, tx.externalId
                    )
                ) {
                    transactionRepository.save(
                        Transaction(
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
                            accountId = resolveAccount(tx.accountName),
                            notes = tx.notes,
                        )
                    )
                    transactionsImported++
                }
            }
        }

        // Insert transfers (dedup)
        var transfersImported = 0

        for (rp in plan.resolvedPortfolios) {
            val realPortfolioId = portfolioIdMap[rp.exported.id] ?: continue
            for (tr in rp.exported.transfers) {
                val realListingId = listingIdMap[tr.listingId] ?: continue
                val sourceAccountId = resolveAccount(tr.sourceAccountName)
                val destinationAccountId = resolveAccount(tr.destinationAccountName)
                if (!transferRepository.existsIdentical(
                        realPortfolioId, realListingId, tr.date,
                        tr.quantity, tr.assetFeeQuantity,
                        sourceAccountId, destinationAccountId,
                    )
                ) {
                    transferRepository.create(
                        Transfer(
                            id = 0,
                            portfolioId = realPortfolioId,
                            listingId = realListingId,
                            assetId = 0, // not used on create
                            quantity = tr.quantity,
                            assetFeeQuantity = tr.assetFeeQuantity,
                            date = tr.date,
                            sourceAccountId = sourceAccountId,
                            destinationAccountId = destinationAccountId,
                            notes = tr.notes,
                        )
                    )
                    transfersImported++
                }
            }
        }

        return ImportResult(assetsCreated, listingsCreated, portfoliosCreated, transactionsImported, transfersImported)
    }
}
