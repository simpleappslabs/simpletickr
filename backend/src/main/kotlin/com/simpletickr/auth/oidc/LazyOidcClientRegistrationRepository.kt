package com.simpletickr.auth.oidc

import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.stereotype.Component

/**
 * Spring Boot's own spring.security.oauth2.client.registration.* auto-configuration calls
 * ClientRegistrations.fromIssuerLocation(...) eagerly while building the ClientRegistrationRepository
 * bean — meaning an unreachable issuer at boot would fail the whole application context. We build
 * our own registration lazily instead: discovery only happens on first actual login attempt, and a
 * failure there just means SSO doesn't work for that request, not that simpletickr won't start.
 */
@Component
class LazyOidcClientRegistrationRepository(
    private val oidcSettings: OidcSettings,
) : ClientRegistrationRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cached: ClientRegistration? = null

    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        if (registrationId != OIDC_REGISTRATION_ID || !oidcSettings.enabled) return null

        cached?.let { return it }

        return try {
            ClientRegistrations.fromIssuerLocation(oidcSettings.issuerUri)
                .registrationId(OIDC_REGISTRATION_ID)
                .clientId(oidcSettings.clientId)
                .clientSecret(oidcSettings.clientSecret)
                .scope("openid", "profile", "email")
                .build()
                .also { cached = it }
        } catch (e: Exception) {
            log.warn("OIDC discovery against {} failed — SSO is unavailable until this succeeds", oidcSettings.issuerUri, e)
            null
        }
    }
}
