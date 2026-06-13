package com.simpletickr.portfolio

import com.simpletickr.asset.AssetRepository
import com.simpletickr.transaction.TransactionRepository
import org.junit.jupiter.api.Test
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

@WebMvcTest(PortfolioController::class)
class PortfolioControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var portfolioRepository: PortfolioRepository

    @MockitoBean
    private lateinit var holdingRepository: HoldingRepository

    @MockitoBean
    private lateinit var transactionRepository: TransactionRepository

    @MockitoBean
    private lateinit var assetRepository: AssetRepository

    @Test
    fun `GET portfolios returns empty list`() {
        whenever(portfolioRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/portfolios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `GET portfolios returns list of portfolios`() {
        whenever(portfolioRepository.findAll()).thenReturn(
            listOf(Portfolio(1L, "My Portfolio"), Portfolio(2L, "Savings"))
        )

        mockMvc.perform(get("/portfolios"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("My Portfolio"))
            .andExpect(jsonPath("$[1].name").value("Savings"))
    }

    @Test
    fun `GET portfolio by id returns 200 when found`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(Portfolio(1L, "My Portfolio"))

        mockMvc.perform(get("/portfolios/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("My Portfolio"))
    }

    @Test
    fun `GET portfolio by id returns 404 when not found`() {
        whenever(portfolioRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(get("/portfolios/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST portfolio creates and returns 201`() {
        whenever(portfolioRepository.save("New Portfolio")).thenReturn(Portfolio(3L, "New Portfolio"))

        mockMvc.perform(
            post("/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"New Portfolio"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.name").value("New Portfolio"))
    }

    @Test
    fun `PUT portfolio returns 200 with updated portfolio`() {
        whenever(portfolioRepository.update(1L, "Renamed")).thenReturn(Portfolio(1L, "Renamed"))

        mockMvc.perform(
            put("/portfolios/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Renamed"))
    }

    @Test
    fun `PUT portfolio returns 404 when not found`() {
        whenever(portfolioRepository.update(99L, "Renamed")).thenReturn(null)

        mockMvc.perform(
            put("/portfolios/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE portfolio returns 204 when found`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(Portfolio(1L, "My Portfolio"))

        mockMvc.perform(delete("/portfolios/1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE portfolio returns 404 when not found`() {
        whenever(portfolioRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(delete("/portfolios/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET holdings returns 404 when portfolio not found`() {
        whenever(portfolioRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(get("/portfolios/99/holdings"))
            .andExpect(status().isNotFound)
    }
}
