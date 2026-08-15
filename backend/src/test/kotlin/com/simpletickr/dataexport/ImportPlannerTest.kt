package com.simpletickr.dataexport

import com.simpletickr.dataexport.model.AssetExport
import com.simpletickr.dataexport.model.ExistingAccount
import com.simpletickr.dataexport.model.ExistingAsset
import com.simpletickr.dataexport.model.ExistingListing
import com.simpletickr.dataexport.model.ExistingPortfolio
import com.simpletickr.dataexport.model.ExistingState
import com.simpletickr.dataexport.model.ExistingTransaction
import com.simpletickr.dataexport.model.ExistingTransfer
import com.simpletickr.dataexport.model.ImportPlanner
import com.simpletickr.dataexport.model.ImportPlanner.AssetMatch
import com.simpletickr.dataexport.model.ListingExport
import com.simpletickr.dataexport.model.PortfolioExport
import com.simpletickr.dataexport.model.PriceMappingExport
import com.simpletickr.dataexport.model.SettingsExport
import com.simpletickr.dataexport.model.SimpletickrExport
import com.simpletickr.dataexport.model.TransactionExport
import com.simpletickr.dataexport.model.TransferExport
import com.simpletickr.transaction.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ImportPlannerTest {

    private val assetUuid = UUID.randomUUID()
    private val portfolioUuid = UUID.randomUUID()

    private val listingExport = ListingExport(
        id = 10L, exchange = "NYSE", ticker = "AAPL", currency = "USD",
        priceMappings = listOf(PriceMappingExport("YAHOO_FINANCE", "AAPL")),
    )
    private val txExport = TransactionExport(
        listingId = 10L, type = "BUY", quantity = BigDecimal("5"),
        price = BigDecimal("150.00"), date = LocalDate.of(2024, 1, 15),
        fees = BigDecimal("2.50"), fxRate = null, externalId = null,
    )
    private val transferExport = TransferExport(
        listingId = 10L, quantity = BigDecimal("3"), assetFeeQuantity = null,
        date = LocalDate.of(2024, 2, 1),
        sourceAccountName = "Exchange", destinationAccountName = "Cold Wallet",
    )
    private val assetExport = AssetExport(
        id = 1L, uuid = assetUuid, isin = "US0378331005", name = "Apple Inc.",
        type = "STOCK", listings = listOf(listingExport),
    )
    private val portfolioExport = PortfolioExport(
        id = 100L, uuid = portfolioUuid, name = "My Portfolio",
        transactions = listOf(txExport),
    )
    private val validExport = SimpletickrExport(
        schemaVersion = 1, exportedAt = Instant.now(),
        settings = SettingsExport("EUR"),
        assets = listOf(assetExport),
        portfolios = listOf(portfolioExport),
    )

    private fun existingAsset(id: Long, uuid: UUID, isin: String?, listings: List<ExistingListing>) =
        ExistingAsset(id, uuid, isin, listings)

    // ── validation ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `unsupported schema version returns error`() {
        val plan = ImportPlanner.plan(validExport.copy(schemaVersion = 99), ExistingState())
        assertTrue(plan.errors.any { it.contains("version") })
        assertTrue(plan.resolvedAssets.isEmpty())
    }

    @Test
    fun `duplicate asset ID in export returns error`() {
        val plan = ImportPlanner.plan(validExport.copy(assets = listOf(assetExport, assetExport)), ExistingState())
        assertTrue(plan.errors.any { it.contains("Duplicate asset") })
    }

    @Test
    fun `transaction referencing missing listing returns error`() {
        val export = validExport.copy(portfolios = listOf(portfolioExport.copy(transactions = listOf(txExport.copy(listingId = 999L)))))
        val plan = ImportPlanner.plan(export, ExistingState())
        assertTrue(plan.errors.any { it.contains("999") })
    }

    @Test
    fun `transfer referencing missing listing returns error`() {
        val export = validExport.copy(portfolios = listOf(portfolioExport.copy(transfers = listOf(transferExport.copy(listingId = 999L)))))
        val plan = ImportPlanner.plan(export, ExistingState())
        assertTrue(plan.errors.any { it.contains("Transfers") && it.contains("999") })
    }

    // ── fresh database ───────────────────────────────────────────────────────────────────────

    @Test
    fun `fresh database counts all entities as to-create`() {
        val analysis = ImportPlanner.plan(validExport, ExistingState()).toAnalysis()
        assertEquals(emptyList<String>(), analysis.errors)
        assertEquals(1, analysis.assetsToCreate)
        assertEquals(1, analysis.listingsToCreate)
        assertEquals(1, analysis.portfoliosToCreate)
        assertEquals(1, analysis.accountsToCreate) // "Default", from the transaction's implicit account
        assertEquals(1, analysis.transactionsToImport)
        assertEquals(0, analysis.transactionsSkipped)
    }

    // ── asset matching ───────────────────────────────────────────────────────────────────────

    @Test
    fun `matchAsset finds existing asset by UUID`() {
        val existing = listOf(existingAsset(1L, assetUuid, "US0378331005", listOf(ExistingListing(10L, "AAPL", "NYSE"))))
        assertEquals(AssetMatch.Found(1L), ImportPlanner.matchAsset(assetExport, existing))
    }

    @Test
    fun `matchAsset falls back to listing key when UUID differs (cross-instance import)`() {
        val differentUuid = UUID.randomUUID()
        val existing = listOf(existingAsset(1L, differentUuid, null, listOf(ExistingListing(10L, "ETH", "CRYPTO"))))
        val cryptoExport = AssetExport(id = 1L, uuid = UUID.randomUUID(), isin = null, name = "Ethereum", type = "CRYPTO",
            listings = listOf(ListingExport(10L, "CRYPTO", "ETH", "USD", emptyList())))
        assertEquals(AssetMatch.Found(1L), ImportPlanner.matchAsset(cryptoExport, existing))
    }

    @Test
    fun `matchAsset returns Ambiguous when two existing assets share the same ISIN`() {
        val existing = listOf(
            existingAsset(1L, UUID.randomUUID(), "US0378331005", emptyList()),
            existingAsset(2L, UUID.randomUUID(), "US0378331005", emptyList()),
        )
        val match = ImportPlanner.matchAsset(assetExport, existing)
        assertTrue(match is AssetMatch.Ambiguous)
        assertTrue((match as AssetMatch.Ambiguous).reason.contains("ISIN"))
    }

    @Test
    fun `matchAsset returns Ambiguous when listing keys match two different existing assets`() {
        val twoListingExport = assetExport.copy(
            isin = null,
            listings = listOf(listingExport, ListingExport(11L, "LSE", "AAPL2", "GBP", emptyList())),
        )
        val existing = listOf(
            existingAsset(1L, UUID.randomUUID(), null, listOf(ExistingListing(10L, "AAPL", "NYSE"))),
            existingAsset(2L, UUID.randomUUID(), null, listOf(ExistingListing(11L, "AAPL2", "LSE"))),
        )
        val match = ImportPlanner.matchAsset(twoListingExport, existing)
        assertTrue(match is AssetMatch.Ambiguous)
        assertTrue((match as AssetMatch.Ambiguous).reason.contains("listing"))
    }

    @Test
    fun `matchAsset returns NotFound when nothing matches`() {
        assertEquals(AssetMatch.NotFound, ImportPlanner.matchAsset(assetExport, emptyList()))
    }

    @Test
    fun `existing asset shows as existing in the plan, with its listing resolved`() {
        val existing = ExistingState(assets = listOf(
            existingAsset(1L, assetUuid, "US0378331005", listOf(ExistingListing(10L, "AAPL", "NYSE"))),
        ))
        val analysis = ImportPlanner.plan(validExport, existing).toAnalysis()
        assertEquals(0, analysis.assetsToCreate)
        assertEquals(1, analysis.assetsExisting)
        assertEquals(0, analysis.listingsToCreate)
        assertEquals(1, analysis.listingsExisting)
    }

    // ── portfolio + transaction dedup ───────────────────────────────────────────────────────

    @Test
    fun `existing portfolio matched by UUID dedups an identical transaction`() {
        val existing = ExistingState(
            assets = listOf(existingAsset(1L, assetUuid, null, listOf(ExistingListing(10L, "AAPL", "NYSE")))),
            portfolios = listOf(ExistingPortfolio(100L, portfolioUuid, "My Portfolio")),
            transactions = listOf(ExistingTransaction(100L, 10L, txExport.date, TransactionType.BUY, txExport.quantity, txExport.price, txExport.fees, null)),
        )
        val analysis = ImportPlanner.plan(validExport, existing).toAnalysis()
        assertEquals(0, analysis.transactionsToImport)
        assertEquals(1, analysis.transactionsSkipped)
    }

    @Test
    fun `transaction dedup ignores BigDecimal scale, matching SQL's numeric equality`() {
        // Existing quantity has trailing zeros; export has none — SQL `quantity = ?` on a numeric
        // column treats these as equal, and the in-memory check must too.
        val existing = ExistingState(
            assets = listOf(existingAsset(1L, assetUuid, null, listOf(ExistingListing(10L, "AAPL", "NYSE")))),
            portfolios = listOf(ExistingPortfolio(100L, portfolioUuid, "My Portfolio")),
            transactions = listOf(ExistingTransaction(100L, 10L, txExport.date, TransactionType.BUY, BigDecimal("5.00"), BigDecimal("150.0000"), BigDecimal("2.5"), null)),
        )
        val analysis = ImportPlanner.plan(validExport, existing).toAnalysis()
        assertEquals(0, analysis.transactionsToImport)
        assertEquals(1, analysis.transactionsSkipped)
    }

    @Test
    fun `new portfolio never dedups even if an identically-shaped transaction exists elsewhere`() {
        val existing = ExistingState(
            assets = listOf(existingAsset(1L, assetUuid, null, listOf(ExistingListing(10L, "AAPL", "NYSE")))),
            transactions = listOf(ExistingTransaction(999L, 10L, txExport.date, TransactionType.BUY, txExport.quantity, txExport.price, txExport.fees, null)),
        )
        val analysis = ImportPlanner.plan(validExport, existing).toAnalysis()
        assertEquals(1, analysis.transactionsToImport)
        assertEquals(0, analysis.transactionsSkipped)
    }

    // ── transfers + accounts ─────────────────────────────────────────────────────────────────

    @Test
    fun `fresh database counts transfer as to-import and its accounts as to-create`() {
        val export = validExport.copy(portfolios = listOf(portfolioExport.copy(transfers = listOf(transferExport))))
        val analysis = ImportPlanner.plan(export, ExistingState()).toAnalysis()
        assertEquals(1, analysis.transfersToImport)
        assertEquals(0, analysis.transfersSkipped)
        // "Exchange", "Cold Wallet", and the transaction's implicit "Default"
        assertEquals(3, analysis.accountsToCreate)
    }

    @Test
    fun `existing transfer is deduped when portfolio, listing and both accounts already exist`() {
        val export = validExport.copy(portfolios = listOf(portfolioExport.copy(transactions = emptyList(), transfers = listOf(transferExport))))
        val existing = ExistingState(
            assets = listOf(existingAsset(1L, assetUuid, null, listOf(ExistingListing(10L, "AAPL", "NYSE")))),
            portfolios = listOf(ExistingPortfolio(100L, portfolioUuid, "My Portfolio")),
            accounts = listOf(ExistingAccount(2L, "Exchange"), ExistingAccount(3L, "Cold Wallet")),
            transfers = listOf(ExistingTransfer(100L, 10L, transferExport.date, transferExport.quantity, null, 2L, 3L)),
        )
        val analysis = ImportPlanner.plan(export, existing).toAnalysis()
        assertEquals(0, analysis.transfersToImport)
        assertEquals(1, analysis.transfersSkipped)
        assertEquals(0, analysis.accountsToCreate)
        assertEquals(2, analysis.accountsExisting)
    }

    @Test
    fun `account explicitly listed in export carries its full detail even if never referenced by a transaction`() {
        val export = validExport.copy(
            accounts = listOf(com.simpletickr.dataexport.model.AccountExport(
                name = "Retirement", broker = "Fidelity", accountType = "RETIREMENT",
                currency = "USD", accountNumber = "123", institution = "Fidelity",
            )),
        )
        val plan = ImportPlanner.plan(export, ExistingState())
        val retirement = plan.resolvedAccounts.first { it.name == "Retirement" }
        assertEquals("Fidelity", retirement.broker)
        assertEquals("RETIREMENT", retirement.accountType)
        assertTrue(retirement.needsCreate)
    }
}
