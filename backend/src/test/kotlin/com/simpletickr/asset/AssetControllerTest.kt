package com.simpletickr.asset

import org.junit.jupiter.api.Test
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

    @Test
    fun `GET assets returns list of assets`() {
        whenever(assetRepository.findAll()).thenReturn(
            listOf(
                Asset(1L, "AAPL", "Apple Inc.", AssetType.STOCK, "USD", null),
                Asset(2L, "BTC", "Bitcoin", AssetType.CRYPTO, "USD", null),
            )
        )

        mockMvc.perform(get("/assets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$[0].type").value("STOCK"))
            .andExpect(jsonPath("$[1].ticker").value("BTC"))
    }

    @Test
    fun `GET asset by id returns 200 when found`() {
        whenever(assetRepository.findById(1L)).thenReturn(
            Asset(1L, "AAPL", "Apple Inc.", AssetType.STOCK, "USD", null)
        )

        mockMvc.perform(get("/assets/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ticker").value("AAPL"))
    }

    @Test
    fun `GET asset by id returns 404 when not found`() {
        whenever(assetRepository.findById(99L)).thenReturn(null)

        mockMvc.perform(get("/assets/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST asset creates and returns 201`() {
        whenever(
            assetRepository.save("NVDA", "NVIDIA Corporation", AssetType.STOCK, "USD", null)
        ).thenReturn(Asset(5L, "NVDA", "NVIDIA Corporation", AssetType.STOCK, "USD", null))

        mockMvc.perform(
            post("/assets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ticker":"NVDA","name":"NVIDIA Corporation","type":"STOCK","currency":"USD"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.ticker").value("NVDA"))
    }
}
