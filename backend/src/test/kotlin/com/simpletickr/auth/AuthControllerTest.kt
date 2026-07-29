package com.simpletickr.auth

import com.simpletickr.auth.usecase.ChangePasswordUseCase
import com.simpletickr.shared.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class)
class AuthControllerTest {

    private val owner = CurrentUser(1L, "admin", "hash")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authenticationManager: AuthenticationManager

    @MockitoBean
    private lateinit var changePasswordUseCase: ChangePasswordUseCase

    @Test
    fun `POST login returns 200 with current user on success`() {
        val authentication = UsernamePasswordAuthenticationToken(owner, "secret", emptyList())
        whenever(authenticationManager.authenticate(any())).thenReturn(authentication)

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"secret"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("admin"))
    }

    @Test
    fun `POST login returns 401 on bad credentials`() {
        whenever(authenticationManager.authenticate(any())).thenThrow(BadCredentialsException("Bad credentials"))

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"wrong"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET me returns 401 when not authenticated`() {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET me returns current user when authenticated`() {
        mockMvc.perform(get("/auth/me").with(user(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("admin"))
    }

    @Test
    fun `POST logout returns 204`() {
        mockMvc.perform(post("/auth/logout").with(user(owner)))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `POST logout returns 401 when not authenticated`() {
        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST change-password returns 204 and delegates to the use case`() {
        mockMvc.perform(
            post("/auth/change-password")
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"old","newPassword":"new"}""")
        )
            .andExpect(status().isNoContent)

        verify(changePasswordUseCase).execute(eq(1L), eq("old"), eq("new"))
    }

    @Test
    fun `POST change-password returns 400 when current password is incorrect`() {
        whenever(changePasswordUseCase.execute(any(), any(), any()))
            .thenThrow(IllegalArgumentException("Current password is incorrect"))

        mockMvc.perform(
            post("/auth/change-password")
                .with(user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"wrong","newPassword":"new"}""")
        )
            .andExpect(status().isBadRequest)
    }
}
