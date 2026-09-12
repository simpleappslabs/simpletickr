package com.simpletickr.health

import com.simpletickr.shared.OidcTestSupportConfig
import com.simpletickr.shared.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HealthController::class)
@Import(SecurityConfig::class, OidcTestSupportConfig::class)
@TestPropertySource(properties = ["app.version=v1.2.3"])
class HealthControllerIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET health returns status and the configured app version`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.version").value("v1.2.3"))
    }
}
