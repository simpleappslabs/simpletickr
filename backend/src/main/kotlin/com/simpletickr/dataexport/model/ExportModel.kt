package com.simpletickr.dataexport.model

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
)

data class ImportResult(
    val assetsCreated: Int,
    val listingsCreated: Int,
    val portfoliosCreated: Int,
    val transactionsImported: Int,
)
