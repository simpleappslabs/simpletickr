package com.simpletickr.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component

/**
 * Both local login (AuthController) and OIDC login/connect (OidcAuthenticationSuccessHandler)
 * place a normalized Principal into the session the same way, including session-fixation
 * protection (a fresh session id once the user is known).
 */
@Component
class SessionPrincipalEstablisher {

    private val securityContextRepository: SecurityContextRepository = HttpSessionSecurityContextRepository()

    fun establish(principal: Principal, request: HttpServletRequest, response: HttpServletResponse) {
        val existingSession = request.getSession(false)
        request.getSession(true)
        if (existingSession != null) request.changeSessionId()

        val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }
}
