package com.simpletickr.asset

import com.simpletickr.price.PriceProviderMapping
import com.simpletickr.shared.CurrencyCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AssetController::class)
class AssetControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var assetService: AssetService
    @MockitoBean private lateinit var createAssetUseCase: CreateAssetUseCase
    @MockitoBean private lateinit var updateAssetUseCase: UpdateAssetUseCase
    @MockitoBean private lateinit var deleteAssetUseCase: DeleteAssetUseCase
    @MockitoBean private lateinit var createListingUseCase: CreateListingUseCase
    @MockitoBean private lateinit var updateListingUseCase: UpdateListingUseCase
    @MockitoBean private lateinit var deleteListingUseCase: DeleteListingUseCase

    private fun listing(id: Long, assetId: Long, ticker: String, currency: String = "USD") =
        Listing(id, assetId, null, ticker, CurrencyCode(currency))

    private fun listingWithPrice(id: Long, assetId: Long, ticker: String, currency: String = "USD") =
        ListingWithPrice(id, assetId, null, ticker, CurrencyCode(currency), null, null)

    private fun asset(id: Long, name: String, type: AssetType, vararg listings: Listing) =
        Asset(id, null, name, type, listings.toList())

    private fun assetWithPrices(id: Long, name: String, type: AssetType, vararg listings: ListingWithPrice) =
        AssetWithPrices(id, null, name, type, listings.toList())

    @Test
    fun `GET assets returns list of assets with listings`() {
        whenever(assetService.listAssets()).thenReturn(
            listOf(
                assetWithPrices(1L, "Apple Inc.", AssetType.STOCK, listingWithPrice(10L, 1L, "AAPL")),
                assetWithPrices(2L, "Bitcoin", AssetType.CRYPTO, listingWithPrice(11L, 2L, "BTC")),
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
        val asset = asset(1L, "Apple Inc.", AssetType.STOCK, listing(10L, 1L, "AAPL"))
        val mappings = mapOf(10L to listOf(PriceProviderMapping(1L, 10L, "YAHOO", "AAPL")))
        whenever(assetService.getAsset(1L)).thenReturn(AssetDetail(asset, mappings))

        mockMvc.perform(get("/assets/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.listings[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$.listings[0].priceMappings[0].provider").value("YAHOO"))
            .andExpect(jsonPath("$.listings[0].priceMappings[0].externalId").value("AAPL"))
    }

    @Test
    fun `GET asset by id returns 404 when not found`() {
        whenever(assetService.getAsset(99L)).thenReturn(null)

        mockMvc.perform(get("/assets/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST asset creates and returns 201`() {
        val result = asset(5L, "NVIDIA Corporation", AssetType.STOCK, listing(20L, 5L, "NVDA"))
        whenever(createAssetUseCase.execute(any())).thenReturn(result)

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
        whenever(deleteAssetUseCase.execute(1L)).thenThrow(
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
