package com.simpletickr.settings

import com.simpletickr.auth.Principal
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.shared.OidcTestSupportConfig
import com.simpletickr.shared.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SettingsController::class)
@Import(SecurityConfig::class, OidcTestSupportConfig::class)
class SettingsControllerIT {

    private val owner = UsernamePasswordAuthenticationToken(Principal.Local(1L, "test-user"), null, emptyList())
    private val other = UsernamePasswordAuthenticationToken(Principal.Local(2L, "other-user"), null, emptyList())

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var settingsService: SettingsService

    @Test
    fun `GET settings returns the current user's own base currency`() {
        whenever(settingsService.getSettings(1L)).thenReturn(UserSettings(CurrencyCode("EUR")))

        mockMvc.perform(get("/settings").with(authentication(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.baseCurrency").value("EUR"))
    }

    @Test
    fun `GET settings for a different user is independent of the caller's own settings`() {
        whenever(settingsService.getSettings(2L)).thenReturn(UserSettings(CurrencyCode("USD")))

        mockMvc.perform(get("/settings").with(authentication(other)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.baseCurrency").value("USD"))
    }

    @Test
    fun `PUT settings updates only the current user's own row`() {
        mockMvc.perform(
            put("/settings")
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"baseCurrency":"GBP"}""")
        )
            .andExpect(status().isOk)

        verify(settingsService).updateSettings(eq(1L), eq(UserSettings(CurrencyCode("GBP"))))
    }
}
