package com.simpletickr.asset

import com.simpletickr.price.PriceProviderMapping
import com.simpletickr.price.PriceProviderMappingRepository
import com.simpletickr.shared.CurrencyCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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

    @MockitoBean
    private lateinit var mappingRepository: PriceProviderMappingRepository

    private fun listing(id: Long, assetId: Long, ticker: String, currency: String = "USD") =
        Listing(id, assetId, null, ticker, CurrencyCode(currency))

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
    fun `GET asset by id returns 200 with listings and price mappings`() {
        whenever(assetRepository.findById(1L)).thenReturn(
            asset(1L, "Apple Inc.", AssetType.STOCK, listing(10L, 1L, "AAPL"))
        )
        whenever(mappingRepository.findByListingIds(listOf(10L))).thenReturn(
            mapOf(10L to listOf(PriceProviderMapping(1L, 10L, "YAHOO", "AAPL")))
        )

        mockMvc.perform(get("/assets/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.listings[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$.listings[0].priceMappings[0].provider").value("YAHOO"))
            .andExpect(jsonPath("$.listings[0].priceMappings[0].externalId").value("AAPL"))
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
                .content("""{"name":"NVIDIA Corporation","type":"STOCK","listings":[{"ticker":"NVDA","currency":"USD"}]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.listings[0].ticker").value("NVDA"))
    }

    @Test
    fun `DELETE asset returns 409 when asset has linked transactions`() {
        whenever(assetRepository.findById(1L)).thenReturn(
            asset(1L, "Apple Inc.", AssetType.STOCK, listing(10L, 1L, "AAPL"))
        )
        whenever(assetRepository.delete(1L)).thenThrow(
            DataIntegrityViolationException("fk_transactions_listing")
        )

        mockMvc.perform(delete("/assets/1"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("This asset cannot be deleted because it has linked transactions."))
    }

    @Test
    fun `POST asset returns 400 when listings is empty`() {
        mockMvc.perform(
            post("/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"NVIDIA Corporation","type":"STOCK","listings":[]}""")
        )
            .andExpect(status().isBadRequest)
    }
}
