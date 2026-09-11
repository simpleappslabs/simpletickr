package com.simpletickr.auth.oidc

import com.simpletickr.auth.Principal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import java.time.Instant
import kotlin.test.assertEquals

class OidcTokenRefreshFilterTest {

    private val authorizedClientManager = mock<OAuth2AuthorizedClientManager>()
    private val authorizedClientRepository = mock<OAuth2AuthorizedClientRepository>()
    private val filter = OidcTokenRefreshFilter(authorizedClientManager, authorizedClientRepository)

    private val request = mock<HttpServletRequest>()
    private val response = MockHttpServletResponse()
    private val filterChain = mock<FilterChain>()

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `passes through untouched when there is no authenticated principal`() {
        filter.doFilterInternal(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(authorizedClientRepository, never()).loadAuthorizedClient<OAuth2AuthorizedClient>(any(), any(), any())
    }

    @Test
    fun `passes through untouched for a local principal`() {
        authenticateAs(Principal.Local(1L, "admin"))

        filter.doFilterInternal(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(authorizedClientRepository, never()).loadAuthorizedClient<OAuth2AuthorizedClient>(any(), any(), any())
    }

    @Test
    fun `passes through without refreshing when the access token is not stale`() {
        val authentication = authenticateAs(Principal.Oidc(1L, "alice", "https://idp.example.com", "sub-1"))
        whenever(authorizedClientRepository.loadAuthorizedClient<OAuth2AuthorizedClient>(OIDC_REGISTRATION_ID, authentication, request))
            .thenReturn(authorizedClient(expiresAt = Instant.now().plusSeconds(3600)))

        filter.doFilterInternal(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(authorizedClientManager, never()).authorize(any())
    }

    @Test
    fun `refreshes a stale access token and continues the chain on success`() {
        val authentication = authenticateAs(Principal.Oidc(1L, "alice", "https://idp.example.com", "sub-1"))
        whenever(authorizedClientRepository.loadAuthorizedClient<OAuth2AuthorizedClient>(OIDC_REGISTRATION_ID, authentication, request))
            .thenReturn(authorizedClient(expiresAt = Instant.now().minusSeconds(60)))

        filter.doFilterInternal(request, response, filterChain)

        verify(authorizedClientManager).authorize(any())
        verify(filterChain).doFilter(request, response)
        assertEquals(200, response.status)
    }

    @Test
    fun `ends the session and returns 401 when refresh fails, instead of continuing the chain`() {
        val authentication = authenticateAs(Principal.Oidc(1L, "alice", "https://idp.example.com", "sub-1"))
        whenever(authorizedClientRepository.loadAuthorizedClient<OAuth2AuthorizedClient>(OIDC_REGISTRATION_ID, authentication, request))
            .thenReturn(authorizedClient(expiresAt = Instant.now().minusSeconds(60)))
        val session = mock<HttpSession>()
        whenever(request.getSession(false)).thenReturn(session)
        whenever(authorizedClientManager.authorize(any()))
            .thenThrow(OAuth2AuthorizationException(OAuth2Error("invalid_grant")))

        filter.doFilterInternal(request, response, filterChain)

        verify(session).invalidate()
        verify(filterChain, never()).doFilter(any(), any())
        assertEquals(401, response.status)
        assertEquals(null, SecurityContextHolder.getContext().authentication)
    }

    private fun authenticateAs(principal: Principal): org.springframework.security.core.Authentication {
        val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        return authentication
    }

    private fun authorizedClient(expiresAt: Instant): OAuth2AuthorizedClient {
        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, "access-token", Instant.now().minusSeconds(7200), expiresAt,
        )
        return OAuth2AuthorizedClient(mock(), "alice", accessToken)
    }
}
