package com.simpletickr.asset

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AssetController::class)
class AssetControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var assetRepository: AssetRepository

    @MockitoBean
    private lateinit var listingRepository: ListingRepository

    private fun listing(id: Long, assetId: Long, ticker: String, currency: String = "USD") =
        Listing(id, assetId, null, ticker, currency)

    private fun asset(id: Long, name: String, type: AssetType, vararg listings: Listing) =
        Asset(id, null, name, type, listings.toList())

    @Test
    fun `GET assets returns list of assets with listings`() {
        whenever(assetRepository.findAll()).thenReturn(
            listOf(
                asset(1L, "Apple Inc.", AssetType.STOCK, listing(10L, 1L, "AAPL")),
                asset(2L, "Bitcoin", AssetType.CRYPTO, listing(11L, 2L, "BTC")),
            )
        )

        mockMvc.perform(get("/assets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].listings[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$[0].type").value("STOCK"))
            .andExpect(jsonPath("$[1].listings[0].ticker").value("BTC"))
    }

    @Test
    fun `GET asset by id returns 200 when found`() {
        whenever(assetRepository.findById(1L)).thenReturn(
            asset(1L, "Apple Inc.", AssetType.STOCK, listing(10L, 1L, "AAPL"))
        )

        mockMvc.perform(get("/assets/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.listings[0].ticker").value("AAPL"))
    }

    @Test
    fun `GET asset by id returns 404 when not found`() {
        whenever(assetRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(get("/assets/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST asset creates and returns 201`() {
        val saved = Asset(5L, null, "NVIDIA Corporation", AssetType.STOCK)
        val withListing = asset(5L, "NVIDIA Corporation", AssetType.STOCK, listing(20L, 5L, "NVDA"))
        whenever(assetRepository.save(anyOrNull(), any(), any())).thenReturn(saved)
        whenever(listingRepository.save(any(), anyOrNull(), any(), any())).thenReturn(listing(20L, 5L, "NVDA"))
        whenever(assetRepository.findById(5L)).thenReturn(withListing)

        mockMvc.perform(
            post("/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"NVIDIA Corporation","type":"STOCK","listing":{"ticker":"NVDA","currency":"USD"}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.listings[0].ticker").value("NVDA"))
    }

    @Test
    fun `POST asset returns 400 when listing is missing`() {
        mockMvc.perform(
            post("/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"NVIDIA Corporation","type":"STOCK"}""")
        )
            .andExpect(status().isBadRequest)
    }
}
