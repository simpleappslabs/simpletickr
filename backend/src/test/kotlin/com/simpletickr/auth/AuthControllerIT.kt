package com.simpletickr.auth

import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.oidc.OidcSettings
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.auth.usecase.ChangePasswordUseCase
import com.simpletickr.shared.OidcTestSupportConfig
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, SessionPrincipalEstablisher::class, OidcTestSupportConfig::class)
class AuthControllerIT {

    private val ownerDetails = LocalUserDetails(1L, "admin", "hash")
    private val ownerPrincipal = Principal.Local(1L, "admin")
    private val ownerAuth = UsernamePasswordAuthenticationToken(ownerPrincipal, null, emptyList())

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authenticationManager: AuthenticationManager

    @MockitoBean
    private lateinit var changePasswordUseCase: ChangePasswordUseCase

    @MockitoBean
    private lateinit var identityRepository: IdentityRepository

    @Autowired
    private lateinit var oidcSettings: OidcSettings

    @Test
    fun `POST login returns 200 with current user on success`() {
        val authentication = UsernamePasswordAuthenticationToken(ownerDetails, "secret", emptyList())
        whenever(authenticationManager.authenticate(any())).thenReturn(authentication)
        whenever(identityRepository.findByUserIdAndProviderType(1L, ProviderType.LOCAL)).thenReturn(
            com.simpletickr.auth.model.Identity(
                id = 1L, userId = 1L, providerType = ProviderType.LOCAL,
                providerId = "local", subject = null, passwordHash = "hash",
            )
        )

        mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"secret"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.localLinked").value(true))
            .andExpect(jsonPath("$.oidcLinked").value(false))
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
        mockMvc.perform(get("/auth/me").with(authentication(ownerAuth)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("admin"))
    }

    @Test
    fun `GET me reports oidcLinked when the user also has an OIDC identity`() {
        whenever(identityRepository.findByUserIdAndProviderType(1L, ProviderType.OIDC)).thenReturn(
            com.simpletickr.auth.model.Identity(
                id = 9L, userId = 1L, providerType = ProviderType.OIDC,
                providerId = "https://idp.example.com", subject = "sub-123", passwordHash = null,
            )
        )

        mockMvc.perform(get("/auth/me").with(authentication(ownerAuth)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.oidcLinked").value(true))
    }

    @Test
    fun `GET me reports localLinked false for an OIDC-only auto-provisioned user`() {
        // No LOCAL identity stubbed — identityRepository returns null for it, as for a real
        // auto-provisioned OIDC-only user (see ResolveOrProvisionOidcUserUseCase).
        mockMvc.perform(get("/auth/me").with(authentication(ownerAuth)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.localLinked").value(false))
    }

    @Test
    fun `POST logout returns 204`() {
        mockMvc.perform(post("/auth/logout").with(authentication(ownerAuth)))
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
                .with(authentication(ownerAuth))
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
                .with(authentication(ownerAuth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"wrong","newPassword":"new"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET auth config returns oidcEnabled from settings, unauthenticated`() {
        whenever(oidcSettings.enabled).thenReturn(true)

        mockMvc.perform(get("/auth/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.oidcEnabled").value(true))
    }
}
