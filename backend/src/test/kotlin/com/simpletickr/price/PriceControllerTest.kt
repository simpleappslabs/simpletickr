package com.simpletickr.price

import com.simpletickr.price.model.PricePoint
import com.simpletickr.price.usecase.DeletePriceMappingUseCase
import com.simpletickr.price.usecase.SetPriceMappingUseCase
import com.simpletickr.price.usecase.SyncPricesUseCase
import com.simpletickr.price.usecase.SyncResult
import com.simpletickr.sync.SyncTrigger
import com.simpletickr.auth.CurrentUser
import com.simpletickr.shared.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@WebMvcTest(PriceController::class)
@Import(SecurityConfig::class)
class PriceControllerTest {

    private val owner = CurrentUser(1L, "test-user", "hash")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var priceQueryService: PriceQueryService
    @MockitoBean private lateinit var syncPricesUseCase: SyncPricesUseCase
    @MockitoBean private lateinit var setPriceMappingUseCase: SetPriceMappingUseCase
    @MockitoBean private lateinit var deletePriceMappingUseCase: DeletePriceMappingUseCase

    private val date = LocalDate.of(2024, 6, 1)
    private val pricePoint = PricePoint(date, BigDecimal("60000.00"))

    @Test
    fun `POST sync listing price history returns 200 with price point`() {
        whenever(syncPricesUseCase.execute(
            from = eq(date), to = eq(date), trigger = eq(SyncTrigger.MANUAL), listingId = eq(10L)
        )).thenReturn(SyncResult(synced = 1, failed = 0))
        whenever(priceQueryService.getPriceHistory(eq(10L), any(), eq(date))).thenReturn(listOf(pricePoint))

        mockMvc.perform(post("/listings/10/price-history/sync").param("date", "2024-06-01").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.date").value("2024-06-01"))
            .andExpect(jsonPath("$.price").value(60000.0))
    }

    @Test
    fun `POST sync listing price history returns 404 when no price mapping configured`() {
        whenever(syncPricesUseCase.execute(
            from = eq(date), to = eq(date), trigger = eq(SyncTrigger.MANUAL), listingId = eq(10L)
        )).thenReturn(SyncResult(synced = 0, failed = 1))

        mockMvc.perform(post("/listings/10/price-history/sync").param("date", "2024-06-01").with(user(owner)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST sync listing price history returns 400 when date is missing`() {
        mockMvc.perform(post("/listings/10/price-history/sync").with(user(owner)))
            .andExpect(status().isBadRequest)
    }
}
