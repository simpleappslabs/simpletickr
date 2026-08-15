package com.simpletickr.dataexport.model

import com.simpletickr.account.model.AccountType
import com.simpletickr.transaction.model.TransactionType
import java.math.BigDecimal

// Pure reconciliation policy for importing a SimpletickrExport into a user's existing data.
// Matches export entities against what already exists, flags ambiguous matches, and decides
// which transactions/transfers are duplicates. No repository calls: ImportDataUseCase fetches
// an ExistingState up front and persists whatever plan() returns. Shapes (ExistingState,
// ResolvedAsset, ImportPlan, etc.) live in ExportModel.kt; this object is the behavior on them.
object ImportPlanner {

    // ── asset matching: UUID → ISIN → listing key, with ambiguity as a typed outcome ────────

    sealed interface AssetMatch {
        data class Found(val id: Long) : AssetMatch
        data object NotFound : AssetMatch
        data class Ambiguous(val reason: String) : AssetMatch
    }

    fun matchAsset(export: AssetExport, existing: List<ExistingAsset>): AssetMatch {
        existing.firstOrNull { it.uuid == export.uuid }?.let { return AssetMatch.Found(it.id) }

        export.isin?.let { isin ->
            val matches = existing.filter { it.isin == isin }
            when {
                matches.size > 1 -> return AssetMatch.Ambiguous(
                    "Ambiguous ISIN match for asset '${export.name}' (ISIN=$isin): ${matches.size} existing assets match.",
                )
                matches.size == 1 -> return AssetMatch.Found(matches[0].id)
                else -> Unit
            }
        }

        val exportKeys = export.listings.map { listingKey(it.ticker, it.exchange) }.toSet()
        val byListingKey = existing.flatMap { asset -> asset.listings.map { listingKey(it.ticker, it.exchange) to asset } }
        val matchedAssets = exportKeys.mapNotNull { key -> byListingKey.firstOrNull { it.first == key }?.second }.distinct()
        return when {
            matchedAssets.size > 1 -> AssetMatch.Ambiguous(
                "Ambiguous listing match for asset '${export.name}': ${matchedAssets.size} existing assets match.",
            )
            matchedAssets.size == 1 -> AssetMatch.Found(matchedAssets[0].id)
            else -> AssetMatch.NotFound
        }
    }

    private fun listingKey(ticker: String, exchange: String?) = "$ticker|$exchange"

    // ── entry point ──────────────────────────────────────────────────────────────────────────

    fun plan(export: SimpletickrExport, existing: ExistingState): ImportPlan {
        if (export.schemaVersion !in 1..3) {
            return invalid(listOf("Unsupported schema version: ${export.schemaVersion}. Supported versions: 1, 2, 3."))
        }

        val exportListingIds = export.assets.flatMap { it.listings }.map { it.id }.toSet()

        val referentialErrors = buildList {
            addAll(duplicateIdErrors("asset", export.assets.map { it.id }))
            addAll(duplicateIdErrors("listing", export.assets.flatMap { it.listings }.map { it.id }))
            addAll(duplicateIdErrors("portfolio", export.portfolios.map { it.id }))

            val badTxRefs = export.portfolios.flatMap { it.transactions }.map { it.listingId }.filter { it !in exportListingIds }.toSet()
            if (badTxRefs.isNotEmpty()) add("Transactions reference listing IDs not present in export: $badTxRefs")

            val badTrRefs = export.portfolios.flatMap { it.transfers }.map { it.listingId }.filter { it !in exportListingIds }.toSet()
            if (badTrRefs.isNotEmpty()) add("Transfers reference listing IDs not present in export: $badTrRefs")
        }
        if (referentialErrors.isNotEmpty()) return invalid(referentialErrors)

        val errors = mutableListOf<String>()

        val resolvedAssets = matchAssets(export.assets, existing.assets, errors)
        val listingIdMap: Map<Long, Long> = resolvedAssets.flatMap { a ->
            a.resolvedListings.mapNotNull { l -> l.existingId?.let { l.exported.id to it } }
        }.toMap()

        val resolvedPortfolios = export.portfolios.map { exportPortfolio ->
            val existingMatch = existing.portfolios.firstOrNull { it.uuid == exportPortfolio.uuid }
                ?: existing.portfolios.firstOrNull { it.name == exportPortfolio.name }
            ResolvedPortfolio(exportPortfolio, existingMatch?.id)
        }
        val portfolioIdMap: Map<Long, Long> = resolvedPortfolios.mapNotNull { rp ->
            rp.existingId?.let { rp.exported.id to it }
        }.toMap()

        val resolvedAccounts = matchAccounts(export, existing.accounts)
        val accountIdByName: Map<String, Long?> = resolvedAccounts.associate { it.name to it.existingId }

        val portfolioPlans = export.portfolios.map { exportPortfolio ->
            planPortfolio(exportPortfolio, portfolioIdMap[exportPortfolio.id], listingIdMap, accountIdByName, existing)
        }

        return ImportPlan(errors, resolvedAssets, resolvedPortfolios, resolvedAccounts, portfolioPlans)
    }

    private fun invalid(errors: List<String>) = ImportPlan(errors, emptyList(), emptyList(), emptyList(), emptyList())

    private fun duplicateIdErrors(kind: String, ids: List<Long>): List<String> {
        val dups = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        return if (dups.isEmpty()) emptyList() else listOf("Duplicate $kind IDs in export: $dups")
    }

    private fun matchAssets(exports: List<AssetExport>, existing: List<ExistingAsset>, errors: MutableList<String>): List<ResolvedAsset> =
        exports.map { exportAsset ->
            val match = matchAsset(exportAsset, existing)
            if (match is AssetMatch.Ambiguous) errors += match.reason
            val existingId = (match as? AssetMatch.Found)?.id
            val existingAsset = existing.firstOrNull { it.id == existingId }

            val resolvedListings = if (existingAsset != null) {
                val existingByKey = existingAsset.listings.associateBy { listingKey(it.ticker, it.exchange) }
                exportAsset.listings.map { el -> ResolvedListing(el, existingByKey[listingKey(el.ticker, el.exchange)]?.id) }
            } else {
                exportAsset.listings.map { ResolvedListing(it, null) }
            }

            ResolvedAsset(exportAsset, existingId, resolvedListings)
        }

    private fun matchAccounts(export: SimpletickrExport, existing: List<ExistingAccount>): List<ResolvedAccount> {
        val existingByName = existing.associateBy { it.name }
        val exportedByName = export.accounts.associateBy { it.name }

        val referencedNames = export.portfolios.flatMap { p ->
            p.transactions.map { it.accountName ?: "Default" } +
                p.transfers.flatMap { listOf(it.sourceAccountName ?: "Default", it.destinationAccountName ?: "Default") }
        }

        val allNames = (exportedByName.keys + referencedNames).distinct()

        return allNames.map { name ->
            val exported = exportedByName[name]
            ResolvedAccount(
                name = name,
                broker = exported?.broker,
                accountType = exported?.accountType ?: AccountType.BROKERAGE.name,
                currency = exported?.currency,
                accountNumber = exported?.accountNumber,
                institution = exported?.institution,
                existingId = existingByName[name]?.id,
            )
        }
    }

    private fun planPortfolio(
        exportPortfolio: PortfolioExport,
        realPortfolioId: Long?,
        listingIdMap: Map<Long, Long>,
        accountIdByName: Map<String, Long?>,
        existing: ExistingState,
    ): PortfolioImportPlan {
        val existingTx = if (realPortfolioId != null) existing.transactions.filter { it.portfolioId == realPortfolioId } else emptyList()
        val existingTr = if (realPortfolioId != null) existing.transfers.filter { it.portfolioId == realPortfolioId } else emptyList()

        val txToInsert = mutableListOf<TransactionExport>()
        var txSkipped = 0
        for (tx in exportPortfolio.transactions) {
            val realListingId = listingIdMap[tx.listingId]
            val type = runCatching { TransactionType.valueOf(tx.type) }.getOrNull()
            val isDuplicate = realPortfolioId != null && realListingId != null && type != null &&
                existingTx.any { e ->
                    e.listingId == realListingId && e.date == tx.date && e.type == type &&
                        e.quantity.compareTo(tx.quantity) == 0 && e.price.compareTo(tx.price) == 0 &&
                        bdEquals(e.fees, tx.fees) && e.externalId == tx.externalId
                }
            if (isDuplicate) txSkipped++ else txToInsert += tx
        }

        val trToInsert = mutableListOf<TransferExport>()
        var trSkipped = 0
        for (tr in exportPortfolio.transfers) {
            val realListingId = listingIdMap[tr.listingId]
            val sourceId = accountIdByName[tr.sourceAccountName ?: "Default"]
            val destinationId = accountIdByName[tr.destinationAccountName ?: "Default"]
            val isDuplicate = realPortfolioId != null && realListingId != null && sourceId != null && destinationId != null &&
                existingTr.any { e ->
                    e.listingId == realListingId && e.date == tr.date &&
                        e.quantity.compareTo(tr.quantity) == 0 && bdEquals(e.assetFeeQuantity, tr.assetFeeQuantity) &&
                        e.sourceAccountId == sourceId && e.destinationAccountId == destinationId
                }
            if (isDuplicate) trSkipped++ else trToInsert += tr
        }

        return PortfolioImportPlan(exportPortfolio.id, txToInsert, txSkipped, trToInsert, trSkipped)
    }

    // Mirrors SQL's `IS NOT DISTINCT FROM`: BigDecimal.equals() is scale-sensitive (100 != 100.00),
    // but the identical-row checks this replaces compare by value, not scale.
    private fun bdEquals(a: BigDecimal?, b: BigDecimal?): Boolean =
        if (a == null || b == null) a == b else a.compareTo(b) == 0
}
