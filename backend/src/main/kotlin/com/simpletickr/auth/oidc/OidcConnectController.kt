package com.simpletickr.auth.oidc

import com.simpletickr.auth.Principal
import com.simpletickr.auth.currentUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Deliberately NOT a generated-interface controller — an OAuth redirect has no JSON
 * request/response shape openapi.yaml can represent (the spec has no redirect responses
 * anywhere). This is the one endpoint in the backend that steps outside that pattern, because
 * the thing it does — kick off a browser redirect — isn't representable any other way.
 */
@RestController
class OidcConnectController(
    private val oidcSettings: OidcSettings,
) {

    @GetMapping("/auth/oidc/connect")
    fun connect(request: HttpServletRequest, response: HttpServletResponse) {
        if (!oidcSettings.enabled) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }

        val principal = currentUser()
        if (principal !is Principal.Local) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Only a local account can connect an OIDC identity")
            return
        }

        // Stashed server-side, read back by the success/failure handlers on callback — this is
        // what lets them tell "this is a connect" apart from a fresh login without trusting
        // anything the client could have supplied on the callback request itself.
        request.session.setAttribute(OIDC_CONNECT_SESSION_ATTRIBUTE, principal.id)
        // sendRedirect resolves a leading "/" against the server root, not the servlet context
        // path, so it has to be prepended by hand or this 404s under server.servlet.context-path.
        response.sendRedirect("${request.contextPath}/oauth2/authorization/$OIDC_REGISTRATION_ID")
    }
}
