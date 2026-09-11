package com.simpletickr.auth.oidc

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

const val OIDC_REGISTRATION_ID = "oidc"

/**
 * Config-presence is the toggle — there is no separate OIDC_ENABLED flag. A self-hoster
 * turns OIDC on simply by setting all three of these; leaving any blank keeps it off.
 */
@Component
class OidcSettings(
    @Value("\${oidc.issuer-uri}") val issuerUri: String,
    @Value("\${oidc.client-id}") val clientId: String,
    @Value("\${oidc.client-secret}") val clientSecret: String,
    @Value("\${oidc.rp-initiated-logout-enabled}") val rpInitiatedLogoutEnabled: Boolean,
    @Value("\${frontend.base-url}") val frontendBaseUrl: String,
) {
    val enabled: Boolean
        get() = issuerUri.isNotBlank() && clientId.isNotBlank() && clientSecret.isNotBlank()
}
