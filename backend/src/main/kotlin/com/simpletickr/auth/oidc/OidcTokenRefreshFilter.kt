package com.simpletickr.auth.oidc

import com.simpletickr.auth.Principal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Business logic runs against Principal.Oidc (see Principal.kt), not Spring's own
 * OAuth2AuthenticationToken, so Spring's usual automatic per-request re-authentication never
 * fires. This filter is the substitute: on a stale access token it refreshes lazily (no
 * scheduled job); a failed refresh means the IdP revoked the user, so the session is killed —
 * that's the actual reason tokens are kept at all rather than discarded after login.
 */
@Component
class OidcTokenRefreshFilter(
    private val authorizedClientManager: OAuth2AuthorizedClientManager,
    private val authorizedClientRepository: OAuth2AuthorizedClientRepository,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    public override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal

        if (authentication == null || principal !is Principal.Oidc) {
            filterChain.doFilter(request, response)
            return
        }

        val authorizedClient = authorizedClientRepository.loadAuthorizedClient<OAuth2AuthorizedClient>(
            OIDC_REGISTRATION_ID, authentication, request,
        )
        val isStale = authorizedClient?.accessToken?.expiresAt?.isBefore(Instant.now()) ?: false

        if (isStale) {
            try {
                authorizedClientManager.authorize(
                    OAuth2AuthorizeRequest.withClientRegistrationId(OIDC_REGISTRATION_ID)
                        .principal(authentication)
                        .attribute(HttpServletRequest::class.java.name, request)
                        .attribute(HttpServletResponse::class.java.name, response)
                        .build()
                )
            } catch (e: OAuth2AuthorizationException) {
                log.warn("OIDC token refresh failed for user {} — ending session", principal.id, e)
                request.getSession(false)?.invalidate()
                SecurityContextHolder.clearContext()
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
