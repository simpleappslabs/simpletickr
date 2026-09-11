package com.simpletickr.auth.oidc

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OidcAuthenticationFailureHandler(
    private val oidcSettings: OidcSettings,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val session = request.getSession(false)
        val isConnectAttempt = session?.getAttribute(OIDC_CONNECT_SESSION_ATTRIBUTE) != null
        session?.removeAttribute(OIDC_CONNECT_SESSION_ATTRIBUTE)

        val path = if (isConnectAttempt) "/settings/security?oidcError=1" else "/login?oidcError=1"
        response.sendRedirect("${oidcSettings.frontendBaseUrl}$path")
    }
}
