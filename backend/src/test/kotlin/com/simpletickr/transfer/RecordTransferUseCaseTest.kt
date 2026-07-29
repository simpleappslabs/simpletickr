package com.simpletickr.transfer

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import com.simpletickr.asset.model.Listing
import com.simpletickr.asset.persistence.ListingRepository
import com.simpletickr.portfolio.HoldingService
import com.simpletickr.portfolio.model.Holding
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.transaction.persistence.TransactionRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class RecordTransferUseCaseTest {

    private val transferRepository = mock<TransferRepository>()
    private val transactionRepository = mock<TransactionRepository>()
    private val listingRepository = mock<ListingRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val holdingService = mock<HoldingService>()

    private val useCase = RecordTransferUseCase(
        transferRepository, transactionRepository, listingRepository, accountRepository, holdingService,
    )

    private val date = LocalDate.of(2024, 6, 1)
    private val ethListing = Listing(id = 20L, assetId = 2L, exchange = null, ticker = "ETH", currency = CurrencyCode("USD"))
    private val sourceAccount = Account(id = 1L, userId = 1L, name = "Exchange", broker = null, accountType = AccountType.CRYPTO, currency = null, accountNumber = null, institution = null)
    private val destinationAccount = Account(id = 2L, userId = 1L, name = "Cold Wallet", broker = null, accountType = AccountType.CRYPTO, currency = null, accountNumber = null, institution = null)

    private val existingHolding = Holding(
        assetId = 2L, assetName = "Ethereum", listingId = 20L, exchange = null, ticker = "ETH",
        currency = CurrencyCode("USD"), quantity = BigDecimal("2.0"),
        avgCostLocal = BigDecimal("2000"), totalCostLocal = BigDecimal("4000"),
    )

    private val command = RecordTransferCommand(
        listingId = 20L,
        quantity = BigDecimal("1.0"),
        assetFeeQuantity = BigDecimal("0.005"),
        date = date,
        sourceAccountId = 1L,
        destinationAccountId = 2L,
    )

    init {
        whenever(transferRepository.create(any())).thenAnswer { inv -> inv.getArgument<Transfer>(0).copy(id = 900L) }
        whenever(listingRepository.findById(20L)).thenReturn(ethListing)
        whenever(accountRepository.isOwnedBy(1L, 1L)).thenReturn(true)
        whenever(accountRepository.isOwnedBy(2L, 1L)).thenReturn(true)
        whenever(transactionRepository.existsForAccountInPortfolio(1L, 5L)).thenReturn(true)
        whenever(transferRepository.existsForAccountInPortfolio(1L, 5L)).thenReturn(false)
        whenever(holdingService.getHoldings(5L, asOf = date)).thenReturn(listOf(existingHolding))
    }

    @Test
    fun `happy path records the transfer with no price or cost basis`() {
        val result = useCase.execute(5L, command, 1L)

        assertEquals(5L, result.portfolioId)
        assertEquals(20L, result.listingId)
        assertEquals(2L, result.assetId)
        assertBd("1.0", result.quantity)
        assertBd("0.005", result.assetFeeQuantity!!)
        assertEquals(1L, result.sourceAccountId)
        assertEquals(2L, result.destinationAccountId)
    }

    @Test
    fun `throws when source and destination accounts are the same`() {
        val sameAccount = command.copy(destinationAccountId = 1L)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, sameAccount, 1L) }
    }

    @Test
    fun `throws when listing not found`() {
        whenever(listingRepository.findById(20L)).thenReturn(null)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command, 1L) }
    }

    @Test
    fun `throws when source account not found`() {
        whenever(accountRepository.isOwnedBy(1L, 1L)).thenReturn(false)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command, 1L) }
    }

    @Test
    fun `throws when destination account not found`() {
        whenever(accountRepository.isOwnedBy(2L, 1L)).thenReturn(false)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command, 1L) }
    }

    @Test
    fun `throws when source account has no prior activity in this portfolio`() {
        whenever(transactionRepository.existsForAccountInPortfolio(1L, 5L)).thenReturn(false)
        whenever(transferRepository.existsForAccountInPortfolio(1L, 5L)).thenReturn(false)
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command, 1L) }
    }

    @Test
    fun `succeeds when destination account has no prior activity in this portfolio - new wallet case`() {
        val result = useCase.execute(5L, command, 1L)
        assertEquals(2L, result.destinationAccountId)
    }

    @Test
    fun `throws when there is no holding of the listing in the portfolio as of the transfer date`() {
        whenever(holdingService.getHoldings(5L, asOf = date)).thenReturn(emptyList())
        assertThrows<IllegalArgumentException> { useCase.execute(5L, command, 1L) }
    }

    @Test
    fun `throws when transferring more than is held as of the transfer date`() {
        val tooMuch = command.copy(quantity = BigDecimal("10"))
        assertThrows<IllegalArgumentException> { useCase.execute(5L, tooMuch, 1L) }
    }

    @Test
    fun `backdating - validates against holdings as of the transfer date, not current holdings`() {
        // "Current" holdings would allow this, but as of the (earlier) transfer date, less was held.
        whenever(holdingService.getHoldings(5L, asOf = date)).thenReturn(listOf(existingHolding.copy(quantity = BigDecimal("0.5"))))
        val tooMuchAsOfDate = command.copy(quantity = BigDecimal("1.0"))
        assertThrows<IllegalArgumentException> { useCase.execute(5L, tooMuchAsOfDate, 1L) }
    }

    private fun assertBd(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "Expected $expected but got $actual")
}
