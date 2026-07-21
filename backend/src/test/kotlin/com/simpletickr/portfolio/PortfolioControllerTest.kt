package com.simpletickr.portfolio

import com.simpletickr.asset.persistence.AssetRepository
import com.simpletickr.price.usecase.BackfillPortfolioPricesUseCase
import com.simpletickr.price.usecase.SyncResult
import com.simpletickr.settings.UserSettings
import com.simpletickr.settings.UserSettingsRepository
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.portfolio.model.AssetHolding
import com.simpletickr.portfolio.model.HoldingWithValuation
import com.simpletickr.portfolio.model.Portfolio
import com.simpletickr.portfolio.model.PortfolioValuationSummary
import com.simpletickr.portfolio.model.PortfolioValuePoint
import com.simpletickr.portfolio.persistence.PortfolioRepository
import java.util.UUID
import com.simpletickr.transaction.persistence.TransactionRepository
import com.simpletickr.transfer.TransferRepository
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

@WebMvcTest(PortfolioController::class)
class PortfolioControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var portfolioRepository: PortfolioRepository

    @MockitoBean
    private lateinit var valuationService: ValuationService

    @MockitoBean
    private lateinit var transactionRepository: TransactionRepository

    @MockitoBean
    private lateinit var transferRepository: TransferRepository

    @MockitoBean
    private lateinit var assetRepository: AssetRepository

    @MockitoBean
    private lateinit var userSettingsRepository: UserSettingsRepository

    @MockitoBean
    private lateinit var backfillPortfolioPricesUseCase: BackfillPortfolioPricesUseCase

    @MockitoBean
    private lateinit var portfolioValueHistoryService: PortfolioValueHistoryService

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
            listOf(Portfolio(1L, UUID(0, 1), "My Portfolio"), Portfolio(2L, UUID(0, 2), "Savings"))
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
        whenever(portfolioRepository.findById(1L)).thenReturn(Portfolio(1L, UUID(0, 1), "My Portfolio"))

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
        whenever(portfolioRepository.save(eq("New Portfolio"), any())).thenReturn(Portfolio(3L, UUID(0, 3), "New Portfolio"))

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
        whenever(portfolioRepository.update(1L, "Renamed")).thenReturn(Portfolio(1L, UUID(0, 1), "Renamed"))

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
        whenever(portfolioRepository.findById(1L)).thenReturn(Portfolio(1L, UUID(0, 1), "My Portfolio"))

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

    @Test
    fun `GET holdings returns 200 with asset-rolled-up holdings`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(Portfolio(1L, UUID(0, 1), "My Portfolio"))
        whenever(userSettingsRepository.find()).thenReturn(UserSettings(CurrencyCode("EUR")))
        whenever(valuationService.getAssetHoldings(1L)).thenReturn(listOf(
            AssetHolding(
                assetId = 5L, assetName = "Request Network",
                totalQuantity = BigDecimal("91"), avgCostBasisBase = BigDecimal("0.70"),
                totalCostBase = BigDecimal("63.70"), marketValueBase = null,
                unrealizedPnlBase = null, unrealizedPnlPct = null,
                listings = emptyList(),
            )
        ))

        mockMvc.perform(get("/portfolios/1/holdings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].assetName").value("Request Network"))
            .andExpect(jsonPath("$[0].baseCurrency").value("EUR"))
            .andExpect(jsonPath("$[0].totalCostBase").value(63.70))
            .andExpect(jsonPath("$[0].marketValueBase").doesNotExist())
    }

    @Test
    fun `GET valuation-summary returns 200 with excluded holdings reported explicitly`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(Portfolio(1L, UUID(0, 1), "My Portfolio"))
        whenever(valuationService.getValuationSummary(1L)).thenReturn(
            PortfolioValuationSummary(
                totalCostBase = BigDecimal("1500"),
                totalMarketValueBase = BigDecimal("1200"),
                totalUnrealizedPnlBase = BigDecimal("-300"),
                totalUnrealizedPnlPct = BigDecimal("-20"),
                excludedHoldingCount = 1,
                excludedHoldingNames = listOf("Request Network"),
            )
        )

        mockMvc.perform(get("/portfolios/1/valuation-summary"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCostBase").value(1500.0))
            .andExpect(jsonPath("$.totalMarketValueBase").value(1200.0))
            .andExpect(jsonPath("$.excludedHoldingCount").value(1))
            .andExpect(jsonPath("$.excludedHoldingNames[0]").value("Request Network"))
    }

    @Test
    fun `GET valuation-summary returns 404 when portfolio not found`() {
        whenever(portfolioRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(get("/portfolios/99/valuation-summary"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST sync-prices returns 200 with sync result`() {
        whenever(backfillPortfolioPricesUseCase.execute(1L)).thenReturn(SyncResult(3, 0))

        mockMvc.perform(post("/portfolios/1/sync-prices"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.synced").value(3))
            .andExpect(jsonPath("$.failed").value(0))
    }

    @Test
    fun `POST sync-prices returns 404 when portfolio not found`() {
        whenever(backfillPortfolioPricesUseCase.execute(99L)).thenReturn(null)

        mockMvc.perform(post("/portfolios/99/sync-prices"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET value-history returns 200 with value and invested points`() {
        whenever(portfolioRepository.findById(1L)).thenReturn(Portfolio(1L, UUID(0, 1), "My Portfolio"))
        whenever(
            portfolioValueHistoryService.getValueHistory(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3))
        ).thenReturn(
            CurrencyCode("EUR") to listOf(
                PortfolioValuePoint(LocalDate.of(2024, 1, 1), BigDecimal("1000.00"), BigDecimal("900.00")),
                PortfolioValuePoint(LocalDate.of(2024, 1, 2), null, BigDecimal("900.00")),
                PortfolioValuePoint(LocalDate.of(2024, 1, 3), BigDecimal("1050.00"), BigDecimal("950.00")),
            )
        )

        mockMvc.perform(get("/portfolios/1/value-history?from=2024-01-01&to=2024-01-03"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.baseCurrency").value("EUR"))
            .andExpect(jsonPath("$.points.length()").value(3))
            .andExpect(jsonPath("$.points[0].value").value(1000.0))
            .andExpect(jsonPath("$.points[0].invested").value(900.0))
            .andExpect(jsonPath("$.points[1].value").doesNotExist())
            .andExpect(jsonPath("$.points[1].invested").value(900.0))
            .andExpect(jsonPath("$.points[2].value").value(1050.0))
    }

    @Test
    fun `GET value-history returns 404 when portfolio not found`() {
        whenever(portfolioRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(get("/portfolios/99/value-history?from=2024-01-01&to=2024-01-03"))
            .andExpect(status().isNotFound)
    }
}
