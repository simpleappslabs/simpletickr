package com.simpletickr.dataexport.model

import com.simpletickr.transaction.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SimpletickrExport(
    val schemaVersion: Int,
    val exportedAt: Instant,
    val settings: SettingsExport,
    val assets: List<AssetExport>,
    val accounts: List<AccountExport> = emptyList(),
    val portfolios: List<PortfolioExport>,
)

data class SettingsExport(val baseCurrency: String)

data class AssetExport(
    val id: Long,
    val uuid: UUID,
    val isin: String?,
    val name: String,
    val type: String,
    val listings: List<ListingExport>,
)

data class ListingExport(
    val id: Long,
    val exchange: String?,
    val ticker: String,
    val currency: String,
    val priceMappings: List<PriceMappingExport>,
)

data class PriceMappingExport(val provider: String, val externalId: String)

data class PortfolioExport(
    val id: Long,
    val uuid: UUID,
    val name: String,
    val transactions: List<TransactionExport>,
    val transfers: List<TransferExport> = emptyList(),
)

data class AccountExport(
    val name: String,
    val broker: String?,
    val accountType: String,
    val currency: String?,
    val accountNumber: String?,
    val institution: String?,
)

data class TransactionExport(
    val listingId: Long,
    val type: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val date: LocalDate,
    val fees: BigDecimal?,
    val fxRate: BigDecimal?,
    val externalId: String?,
    val accountName: String? = null,
    val notes: String? = null,
)

data class TransferExport(
    val listingId: Long,
    val quantity: BigDecimal,
    val assetFeeQuantity: BigDecimal?,
    val date: LocalDate,
    val sourceAccountName: String?,
    val destinationAccountName: String?,
    val notes: String? = null,
)

data class ImportAnalysis(
    val errors: List<String>,
    val assetsToCreate: Int,
    val assetsExisting: Int,
    val listingsToCreate: Int,
    val listingsExisting: Int,
    val portfoliosToCreate: Int,
    val portfoliosExisting: Int,
    val transactionsToImport: Int,
    val transactionsSkipped: Int,
    val transfersToImport: Int = 0,
    val transfersSkipped: Int = 0,
    val accountsToCreate: Int = 0,
    val accountsExisting: Int = 0,
)

data class ImportResult(
    val assetsCreated: Int,
    val listingsCreated: Int,
    val portfoliosCreated: Int,
    val transactionsImported: Int,
    val transfersImported: Int = 0,
    val accountsCreated: Int = 0,
)

// ── existing-state snapshot: what ImportDataUseCase fetches up front for ImportPlanner ─────────

data class ExistingListing(val id: Long, val ticker: String, val exchange: String?)
data class ExistingAsset(val id: Long, val uuid: UUID, val isin: String?, val listings: List<ExistingListing>)
data class ExistingPortfolio(val id: Long, val uuid: UUID, val name: String)
data class ExistingAccount(val id: Long, val name: String)

data class ExistingTransaction(
    val portfolioId: Long, val listingId: Long, val date: LocalDate, val type: TransactionType,
    val quantity: BigDecimal, val price: BigDecimal, val fees: BigDecimal?, val externalId: String?,
)

data class ExistingTransfer(
    val portfolioId: Long, val listingId: Long, val date: LocalDate,
    val quantity: BigDecimal, val assetFeeQuantity: BigDecimal?,
    val sourceAccountId: Long, val destinationAccountId: Long,
)

data class ExistingState(
    val assets: List<ExistingAsset> = emptyList(),
    val portfolios: List<ExistingPortfolio> = emptyList(),
    val accounts: List<ExistingAccount> = emptyList(),
    val transactions: List<ExistingTransaction> = emptyList(),
    val transfers: List<ExistingTransfer> = emptyList(),
)

// ── plan: what ImportPlanner.plan() decides to do with each export entity ──────────────────────

data class ResolvedListing(val exported: ListingExport, val existingId: Long?) {
    val needsCreate get() = existingId == null
}

data class ResolvedAsset(val exported: AssetExport, val existingId: Long?, val resolvedListings: List<ResolvedListing>) {
    val needsCreate get() = existingId == null
}

data class ResolvedPortfolio(val exported: PortfolioExport, val existingId: Long?) {
    val needsCreate get() = existingId == null
}

data class ResolvedAccount(
    val name: String,
    val broker: String?,
    val accountType: String,
    val currency: String?,
    val accountNumber: String?,
    val institution: String?,
    val existingId: Long?,
) {
    val needsCreate get() = existingId == null
}

data class PortfolioImportPlan(
    val exportPortfolioId: Long,
    val transactionsToInsert: List<TransactionExport>,
    val transactionsSkipped: Int,
    val transfersToInsert: List<TransferExport>,
    val transfersSkipped: Int,
)

data class ImportPlan(
    val errors: List<String>,
    val resolvedAssets: List<ResolvedAsset>,
    val resolvedPortfolios: List<ResolvedPortfolio>,
    val resolvedAccounts: List<ResolvedAccount>,
    val portfolioPlans: List<PortfolioImportPlan>,
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
        accountsToCreate = resolvedAccounts.count { it.needsCreate },
        accountsExisting = resolvedAccounts.count { !it.needsCreate },
        transactionsToImport = portfolioPlans.sumOf { it.transactionsToInsert.size },
        transactionsSkipped = portfolioPlans.sumOf { it.transactionsSkipped },
        transfersToImport = portfolioPlans.sumOf { it.transfersToInsert.size },
        transfersSkipped = portfolioPlans.sumOf { it.transfersSkipped },
    )
}
