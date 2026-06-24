package com.simpletickr.transaction

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

    private val sample = Transaction(
        id = 1L, portfolioId = 10L, listingId = 5L, assetId = 2L,
        type = TransactionType.BUY,
        quantity = BigDecimal("5"), price = BigDecimal("100"),
        date = LocalDate.of(2024, 1, 15), fees = null,
    )

    @Test
    fun `GET transactions returns paginated response filtered by portfolioId`() {
        whenever(transactionRepository.findAll(10L, 0, 25)).thenReturn(listOf(sample))
        whenever(transactionRepository.count(10L)).thenReturn(1L)

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
        whenever(transactionRepository.findAll(10L, 1, 10)).thenReturn(emptyList())
        whenever(transactionRepository.count(10L)).thenReturn(15L)

        mockMvc.perform(get("/transactions?portfolioId=10&page=1&size=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(15))
            .andExpect(jsonPath("$.totalPages").value(2))
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
                .content("""{"listingId":5,"type":"BUY","quantity":5.0,"price":100.0,"date":"2024-01-15"}""")
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
                .content("""{"listingId":5,"type":"BUY","quantity":10.0,"price":100.0,"date":"2024-01-15"}""")
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
                .content("""{"listingId":5,"type":"BUY","quantity":10.0,"price":100.0,"date":"2024-01-15"}""")
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
                .content("""{"listingId":5,"type":"BUY","quantity":0.0,"price":100.0,"date":"2024-01-15"}""")
        )
            .andExpect(status().isBadRequest)
    }
}
