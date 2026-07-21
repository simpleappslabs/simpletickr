package com.simpletickr.transaction

import com.simpletickr.account.AccountService
import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.transaction.model.Transaction
import com.simpletickr.transaction.model.TransactionType
import com.simpletickr.transaction.persistence.TransactionFilter
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.trade.RecordCryptoTradeUseCase
import com.simpletickr.transaction.usecase.AmendTransactionUseCase
import com.simpletickr.transaction.usecase.DeleteTransactionUseCase
import com.simpletickr.transaction.usecase.RecordTransactionUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(TransactionController::class)
class TransactionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var transactionRepository: TransactionRepository
    @MockitoBean private lateinit var recordTransactionUseCase: RecordTransactionUseCase
    @MockitoBean private lateinit var amendTransactionUseCase: AmendTransactionUseCase
    @MockitoBean private lateinit var deleteTransactionUseCase: DeleteTransactionUseCase
    @MockitoBean private lateinit var recordCryptoTradeUseCase: RecordCryptoTradeUseCase
    @MockitoBean private lateinit var accountService: AccountService

    private val sampleAccount = Account(id = 1L, name = "Test Account", broker = null, accountType = AccountType.BROKERAGE, currency = null, accountNumber = null, institution = null)

    private val sample = Transaction(
        id = 1L, portfolioId = 10L, listingId = 5L, assetId = 2L,
        type = TransactionType.BUY,
        quantity = BigDecimal("5"), price = BigDecimal("100"),
        date = LocalDate.of(2024, 1, 15), fees = null,
        accountId = 1L,
    )

    @BeforeEach
    fun stubAccounts() {
        whenever(accountService.listAccounts()).thenReturn(listOf(sampleAccount))
        whenever(accountService.getAccount(1L)).thenReturn(sampleAccount)
    }

    @Test
    fun `GET transactions returns paginated response filtered by portfolioId`() {
        whenever(transactionRepository.findAll(TransactionFilter(portfolioId = 10L), 0, 25)).thenReturn(listOf(sample))
        whenever(transactionRepository.count(TransactionFilter(portfolioId = 10L))).thenReturn(1L)

        mockMvc.perform(get("/transactions?portfolioId=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].portfolioId").value(10))
            .andExpect(jsonPath("$.items[0].listingId").value(5))
            .andExpect(jsonPath("$.items[0].assetId").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(25))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
    }

    @Test
    fun `GET transactions respects page and size params`() {
        whenever(transactionRepository.findAll(TransactionFilter(portfolioId = 10L), 1, 10)).thenReturn(emptyList())
        whenever(transactionRepository.count(TransactionFilter(portfolioId = 10L))).thenReturn(15L)

        mockMvc.perform(get("/transactions?portfolioId=10&page=1&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(15))
            .andExpect(jsonPath("$.totalPages").value(2))
    }

    @Test
    fun `GET transactions filters by type`() {
        whenever(transactionRepository.findAll(TransactionFilter(type = TransactionType.BUY), 0, 25)).thenReturn(listOf(sample))
        whenever(transactionRepository.count(TransactionFilter(type = TransactionType.BUY))).thenReturn(1L)

        mockMvc.perform(get("/transactions?type=BUY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].type").value("BUY"))
    }

    @Test
    fun `GET transactions filters by listingId`() {
        whenever(transactionRepository.findAll(TransactionFilter(listingId = 5L), 0, 25)).thenReturn(listOf(sample))
        whenever(transactionRepository.count(TransactionFilter(listingId = 5L))).thenReturn(1L)

        mockMvc.perform(get("/transactions?listingId=5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].listingId").value(5))
    }

    @Test
    fun `GET transactions filters by date range`() {
        val filter = TransactionFilter(
            dateFrom = LocalDate.of(2024, 1, 1),
            dateTo = LocalDate.of(2024, 12, 31),
        )
        whenever(transactionRepository.findAll(filter, 0, 25)).thenReturn(listOf(sample))
        whenever(transactionRepository.count(filter)).thenReturn(1L)

        mockMvc.perform(get("/transactions?dateFrom=2024-01-01&dateTo=2024-12-31"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
    }

    @Test
    fun `GET transactions returns 400 when dateFrom is after dateTo`() {
        mockMvc.perform(get("/transactions?dateFrom=2024-12-31&dateTo=2024-01-01"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET transactions returns 400 when size is zero`() {
        mockMvc.perform(get("/transactions?size=0"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET transactions returns 400 when size exceeds maximum`() {
        mockMvc.perform(get("/transactions?size=201"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET transactions returns 400 when page is negative`() {
        mockMvc.perform(get("/transactions?page=-1"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET transaction by id returns 200 when found`() {
        whenever(transactionRepository.findById(1L)).thenReturn(sample)

        mockMvc.perform(get("/transactions/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.type").value("BUY"))
    }

    @Test
    fun `GET transaction by id returns 404 when not found`() {
        whenever(transactionRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(get("/transactions/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST portfolio transaction returns 201`() {
        whenever(recordTransactionUseCase.execute(eq(10L), any())).thenReturn(sample)

        mockMvc.perform(
            post("/portfolios/10/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"listingId":5,"type":"BUY","quantity":5.0,"price":100.0,"date":"2024-01-15","accountId":1}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.type").value("BUY"))
    }

    @Test
    fun `PUT portfolio transaction returns 200`() {
        whenever(amendTransactionUseCase.execute(eq(10L), eq(1L), any()))
            .thenReturn(sample.copy(quantity = BigDecimal("10")))

        mockMvc.perform(
            put("/portfolios/10/transactions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"listingId":5,"type":"BUY","quantity":10.0,"price":100.0,"date":"2024-01-15","accountId":1}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quantity").value(10.0))
    }

    @Test
    fun `PUT portfolio transaction returns 404 when not found`() {
        whenever(amendTransactionUseCase.execute(eq(10L), eq(99L), any())).thenReturn(null)

        mockMvc.perform(
            put("/portfolios/10/transactions/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"listingId":5,"type":"BUY","quantity":10.0,"price":100.0,"date":"2024-01-15","accountId":1}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE portfolio transaction returns 204`() {
        whenever(deleteTransactionUseCase.execute(10L, 1L)).thenReturn(true)

        mockMvc.perform(delete("/portfolios/10/transactions/1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE portfolio transaction returns 404 when not found`() {
        whenever(deleteTransactionUseCase.execute(10L, 99L)).thenReturn(false)

        mockMvc.perform(delete("/portfolios/10/transactions/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST returns 400 for invalid quantity`() {
        whenever(recordTransactionUseCase.execute(eq(10L), any()))
            .thenThrow(IllegalArgumentException("Quantity must be positive"))

        mockMvc.perform(
            post("/portfolios/10/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"listingId":5,"type":"BUY","quantity":0.0,"price":100.0,"date":"2024-01-15","accountId":1}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST split transaction returns 201`() {
        val splitTx = sample.copy(type = TransactionType.SPLIT, quantity = BigDecimal("2"), price = BigDecimal.ZERO)
        whenever(recordTransactionUseCase.execute(eq(10L), any())).thenReturn(splitTx)

        mockMvc.perform(
            post("/portfolios/10/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"listingId":5,"type":"SPLIT","quantity":2.0,"price":0.0,"date":"2024-06-01","accountId":1}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("SPLIT"))
    }

    @Test
    fun `POST crypto trade returns 201 with sell and buy legs`() {
        val sellTx = sample.copy(id = 101L, type = TransactionType.SELL, tradeId = 99L)
        val buyTx = sample.copy(id = 102L, listingId = 6L, type = TransactionType.BUY, tradeId = 99L)
        val trade = com.simpletickr.trade.CryptoTrade(id = 99L, portfolioId = 10L, sell = sellTx, buy = buyTx)
        whenever(recordCryptoTradeUseCase.execute(eq(10L), any())).thenReturn(trade)

        mockMvc.perform(
            post("/portfolios/10/transactions/trade")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sellListingId":5,"sellQuantity":0.1,"sellPrice":60000.0,"buyListingId":6,"buyQuantity":2.5,"buyPrice":2400.0,"date":"2024-06-01","accountId":1}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(99))
            .andExpect(jsonPath("$.sell.type").value("SELL"))
            .andExpect(jsonPath("$.buy.type").value("BUY"))
            .andExpect(jsonPath("$.sell.tradeId").value(99))
            .andExpect(jsonPath("$.buy.tradeId").value(99))
    }

    @Test
    fun `POST crypto trade returns 400 when use case throws`() {
        whenever(recordCryptoTradeUseCase.execute(eq(10L), any()))
            .thenThrow(IllegalArgumentException("Buy listing must belong to a CRYPTO asset"))

        mockMvc.perform(
            post("/portfolios/10/transactions/trade")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sellListingId":5,"sellQuantity":0.1,"sellPrice":60000.0,"buyListingId":6,"buyQuantity":2.5,"buyPrice":2400.0,"date":"2024-06-01","accountId":1}""")
        )
            .andExpect(status().isBadRequest)
    }
}
