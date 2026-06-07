package com.simpletickr.transaction

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(TransactionController::class)
class TransactionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var transactionRepository: TransactionRepository

    private val sample = Transaction(
        id = 1L,
        portfolioId = 10L,
        assetId = 2L,
        type = TransactionType.BUY,
        quantity = BigDecimal("5.0"),
        price = BigDecimal("100.0"),
        date = LocalDate.of(2024, 1, 15),
        fees = null,
    )

    private val otherPortfolioSample = Transaction(
        id = 2L,
        portfolioId = 99L,
        assetId = 3L,
        type = TransactionType.SELL,
        quantity = BigDecimal("2.0"),
        price = BigDecimal("50.0"),
        date = LocalDate.of(2024, 2, 1),
        fees = null,
    )

    @Test
    fun `GET transactions returns list filtered by portfolioId`() {
        whenever(transactionRepository.findAll(10L)).thenReturn(listOf(sample))
        whenever(transactionRepository.findAll(99L)).thenReturn(listOf(otherPortfolioSample))

        mockMvc.perform(get("/transactions?portfolioId=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].portfolioId").value(10))
    }

    @Test
    fun `GET transactions does not return transactions from other portfolios`() {
        whenever(transactionRepository.findAll(10L)).thenReturn(listOf(sample))
        whenever(transactionRepository.findAll(99L)).thenReturn(listOf(otherPortfolioSample))

        mockMvc.perform(get("/transactions?portfolioId=10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].portfolioId").value(10))

        mockMvc.perform(get("/transactions?portfolioId=99"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].portfolioId").value(99))
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
    fun `PUT transaction returns 200 with updated transaction`() {
        whenever(transactionRepository.update(eq(1L), any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(sample.copy(quantity = BigDecimal("10.0")))

        mockMvc.perform(
            put("/transactions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"portfolioId":10,"assetId":2,"type":"BUY","quantity":10.0,"price":100.0,"date":"2024-01-15"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.quantity").value(10.0))
    }

    @Test
    fun `PUT transaction returns 404 when not found`() {
        whenever(transactionRepository.update(eq(99L), any(), any(), any(), any(), any(), any()))
            .thenReturn(null)

        mockMvc.perform(
            put("/transactions/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"portfolioId":10,"assetId":2,"type":"BUY","quantity":10.0,"price":100.0,"date":"2024-01-15"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE transaction returns 204 when found`() {
        whenever(transactionRepository.findById(1L)).thenReturn(sample)

        mockMvc.perform(delete("/transactions/1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE transaction returns 404 when not found`() {
        whenever(transactionRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(delete("/transactions/99"))
            .andExpect(status().isNotFound)
    }
}
