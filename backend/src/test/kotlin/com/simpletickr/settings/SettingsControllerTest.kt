package com.simpletickr.settings

import com.simpletickr.auth.CurrentUser
import com.simpletickr.shared.CurrencyCode
import com.simpletickr.shared.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SettingsController::class)
@Import(SecurityConfig::class)
class SettingsControllerTest {

    private val owner = CurrentUser(1L, "test-user", "hash")
    private val other = CurrentUser(2L, "other-user", "hash")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userSettingsRepository: UserSettingsRepository

    @Test
    fun `GET settings returns the current user's own base currency`() {
        whenever(userSettingsRepository.find(1L)).thenReturn(UserSettings(CurrencyCode("EUR")))

        mockMvc.perform(get("/settings").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.baseCurrency").value("EUR"))
    }

    @Test
    fun `GET settings for a different user is independent of the caller's own settings`() {
        whenever(userSettingsRepository.find(2L)).thenReturn(UserSettings(CurrencyCode("USD")))

        mockMvc.perform(get("/settings").with(user(other)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.baseCurrency").value("USD"))
    }

    @Test
    fun `PUT settings updates only the current user's own row`() {
        mockMvc.perform(
            put("/settings")
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"baseCurrency":"GBP"}""")
        )
            .andExpect(status().isOk)

        verify(userSettingsRepository).update(eq(1L), eq(UserSettings(CurrencyCode("GBP"))))
    }
}
