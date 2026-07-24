package com.simpletickr.dataexport

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.Asset
import com.simpletickr.asset.model.AssetType
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.portfolio.model.Portfolio
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class ExportServiceTest {

    private val assetRepository = mock<AssetRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val mappingRepository = mock<PriceProviderMappingRepository>()
    private val portfolioRepository = mock<PortfolioRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val transferRepository = mock<TransferRepository>()
    private val settingsRepository = mock<UserSettingsRepository>()

    private val service = ExportService(
        assetRepository, accountRepository, mappingRepository,
        portfolioRepository, transactionRepository, transferRepository, settingsRepository,
    )

    private val eur = CurrencyCode("EUR")

    private fun asset(id: Long, listingId: Long) = Asset(
        id = id, uuid = UUID.randomUUID(), isin = null, name = "Asset $id", type = AssetType.STOCK,
        listings = listOf(Listing(id = listingId, assetId = id, exchange = "NASDAQ", ticker = "T$id", currency = eur)),
    )

    private fun account(id: Long, name: String) = Account(
        id = id, name = name, broker = null, accountType = AccountType.BROKERAGE,
        currency = null, accountNumber = null, institution = null,
    )

    private fun portfolio(id: Long, name: String) = Portfolio(id = id, uuid = UUID.randomUUID(), name = name)

    private fun transaction(portfolioId: Long, listingId: Long, accountId: Long) = Transaction(
        id = 0, portfolioId = portfolioId, listingId = listingId, assetId = 0,
        type = TransactionType.BUY, quantity = BigDecimal.ONE, price = BigDecimal.TEN,
        date = LocalDate.now(), fees = null, accountId = accountId,
    )

    private fun transfer(portfolioId: Long, listingId: Long, sourceId: Long, destId: Long) = Transfer(
        id = 0, portfolioId = portfolioId, listingId = listingId, assetId = 0,
        quantity = BigDecimal.ONE, date = LocalDate.now(),
        sourceAccountId = sourceId, destinationAccountId = destId,
    )

    @Test
    fun `buildExport with no portfolioIds returns everything, including unreferenced assets and accounts`() {
        whenever(settingsRepository.find()).thenReturn(UserSettings(eur))
        val portfolios = listOf(portfolio(1L, "P1"), portfolio(2L, "P2"))
        whenever(portfolioRepository.findAll()).thenReturn(portfolios)
        whenever(assetRepository.findAll()).thenReturn(listOf(asset(100L, 1000L), asset(200L, 2000L)))
        whenever(accountRepository.findAll()).thenReturn(listOf(account(10L, "A1"), account(20L, "A2")))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        whenever(transactionRepository.findAllForPortfolio(1L)).thenReturn(listOf(transaction(1L, 1000L, 10L)))
        whenever(transactionRepository.findAllForPortfolio(2L)).thenReturn(emptyList())
        whenever(transferRepository.findAllForPortfolio(1L)).thenReturn(emptyList())
        whenever(transferRepository.findAllForPortfolio(2L)).thenReturn(emptyList())

        val export = service.buildExport(null)

        assertEquals(2, export.portfolios.size)
        assertEquals(2, export.assets.size) // asset 200 unreferenced by any transaction, still included
        assertEquals(2, export.accounts.size) // account 20 unreferenced, still included
    }

    @Test
    fun `buildExport scoped to one portfolio only includes assets and accounts it references`() {
        whenever(settingsRepository.find()).thenReturn(UserSettings(eur))
        whenever(portfolioRepository.findByIds(setOf(1L))).thenReturn(listOf(portfolio(1L, "P1")))
        whenever(assetRepository.findAll()).thenReturn(listOf(asset(100L, 1000L), asset(200L, 2000L)))
        whenever(accountRepository.findAll()).thenReturn(listOf(account(10L, "A1"), account(20L, "A2")))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        whenever(transactionRepository.findAllForPortfolio(1L)).thenReturn(listOf(transaction(1L, 1000L, 10L)))
        whenever(transferRepository.findAllForPortfolio(1L)).thenReturn(emptyList())

        val export = service.buildExport(listOf(1L))

        assertEquals(1, export.portfolios.size)
        assertEquals(1, export.assets.size)
        assertEquals(100L, export.assets.single().id)
        assertEquals(1, export.accounts.size)
        assertEquals("A1", export.accounts.single().name)
    }

    @Test
    fun `buildExport scoped to a portfolio includes assets and accounts referenced only via transfers`() {
        whenever(settingsRepository.find()).thenReturn(UserSettings(eur))
        whenever(portfolioRepository.findByIds(setOf(1L))).thenReturn(listOf(portfolio(1L, "P1")))
        whenever(assetRepository.findAll()).thenReturn(listOf(asset(100L, 1000L), asset(200L, 2000L)))
        whenever(accountRepository.findAll()).thenReturn(listOf(account(10L, "A1"), account(20L, "A2"), account(30L, "A3")))
        whenever(mappingRepository.findAll()).thenReturn(emptyList())
        whenever(transactionRepository.findAllForPortfolio(1L)).thenReturn(emptyList())
        whenever(transferRepository.findAllForPortfolio(1L)).thenReturn(listOf(transfer(1L, 2000L, 20L, 30L)))

        val export = service.buildExport(listOf(1L))

        assertEquals(1, export.assets.size)
        assertEquals(200L, export.assets.single().id)
        assertEquals(2, export.accounts.size)
        assertTrue(export.accounts.any { it.name == "A2" })
        assertTrue(export.accounts.any { it.name == "A3" })
    }
}
