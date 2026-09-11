package com.simpletickr.auth.oidc

import com.simpletickr.auth.Principal
import com.simpletickr.auth.SessionPrincipalEstablisher
import com.simpletickr.auth.usecase.ConnectOidcIdentityUseCase
import com.simpletickr.auth.usecase.IdentityAlreadyLinkedException
import com.simpletickr.auth.usecase.OidcClaims
import com.simpletickr.auth.usecase.ResolveOrProvisionOidcUserUseCase
import com.simpletickr.user.persistence.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OidcAuthenticationSuccessHandler(
    private val resolveOrProvisionOidcUserUseCase: ResolveOrProvisionOidcUserUseCase,
    private val connectOidcIdentityUseCase: ConnectOidcIdentityUseCase,
    private val userRepository: UserRepository,
    private val sessionPrincipalEstablisher: SessionPrincipalEstablisher,
    private val oidcSettings: OidcSettings,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oidcUser = authentication.principal as OidcUser
        val claims = OidcClaims(
            providerId = oidcUser.issuer.toString(),
            subject = oidcUser.subject,
            preferredUsername = oidcUser.claims["preferred_username"] as? String,
            email = oidcUser.claims["email"] as? String,
        )

        val session = request.session
        val connectUserId = session.getAttribute(OIDC_CONNECT_SESSION_ATTRIBUTE) as? Long
        session.removeAttribute(OIDC_CONNECT_SESSION_ATTRIBUTE)

        if (connectUserId != null) {
            handleConnect(connectUserId, claims, request, response)
        } else {
            handleLogin(claims, request, response)
        }
    }

    private fun handleConnect(
        connectUserId: Long,
        claims: OidcClaims,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val user = userRepository.findById(connectUserId)
            ?: error("Connected identity references missing user $connectUserId")

        // Spring's OAuth2 login filter already authenticated and persisted the raw OIDC
        // principal into the session before this handler ever runs — from its point of view, a
        // valid ID token was presented, so authentication succeeded, regardless of what our own
        // business rule below decides. So on every path out of here the session must end up
        // back on the local principal that was already logged in — it must never be left
        // holding that raw OIDC principal, which nothing else in the app knows how to handle.
        try {
            connectOidcIdentityUseCase.execute(connectUserId, claims)
        } catch (e: IdentityAlreadyLinkedException) {
            sessionPrincipalEstablisher.establish(Principal.Local(user.id, user.username), request, response)
            response.sendRedirect(frontendUrl("/settings/security?oidcError=already-linked"))
            return
        }

        sessionPrincipalEstablisher.establish(Principal.Local(user.id, user.username), request, response)
        response.sendRedirect(frontendUrl("/settings/security?oidcConnected=true"))
    }

    private fun handleLogin(claims: OidcClaims, request: HttpServletRequest, response: HttpServletResponse) {
        val user = resolveOrProvisionOidcUserUseCase.execute(claims)
        val principal = Principal.Oidc(
            id = user.id,
            username = user.username,
            providerId = claims.providerId,
            subject = claims.subject,
        )
        sessionPrincipalEstablisher.establish(principal, request, response)
        response.sendRedirect(frontendUrl("/"))
    }

    private fun frontendUrl(path: String) = "${oidcSettings.frontendBaseUrl}$path"
}
