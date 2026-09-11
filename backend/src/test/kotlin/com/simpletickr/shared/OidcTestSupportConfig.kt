package com.simpletickr.shared

import com.simpletickr.auth.oidc.LazyOidcClientRegistrationRepository
import com.simpletickr.auth.oidc.OidcAuthenticationFailureHandler
import com.simpletickr.auth.oidc.OidcAuthenticationSuccessHandler
import com.simpletickr.auth.oidc.OidcSettings
import com.simpletickr.auth.oidc.OidcTokenRefreshFilter
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository

/**
 * SecurityConfig depends on these OIDC beans regardless of whether OIDC is enabled, so every
 * @WebMvcTest that @Imports SecurityConfig needs them satisfied. A mock OidcSettings.enabled
 * defaults to false, so the .oauth2Login() DSL branch is never exercised in these slice tests.
 * OidcTokenRefreshFilter is real, not mocked — it's unconditionally added to the filter chain,
 * and a mocked filter would no-op instead of calling the chain, breaking every request.
 */
@TestConfiguration
class OidcTestSupportConfig {

    @Bean
    fun oidcSettings(): OidcSettings = mock()

    @Bean
    fun lazyOidcClientRegistrationRepository(): LazyOidcClientRegistrationRepository = mock()

    @Bean
    fun oauth2AuthorizedClientRepository(): OAuth2AuthorizedClientRepository = mock()

    @Bean
    fun oidcAuthenticationSuccessHandler(): OidcAuthenticationSuccessHandler = mock()

    @Bean
    fun oidcAuthenticationFailureHandler(): OidcAuthenticationFailureHandler = mock()

    @Bean
    fun oidcAuthorizedClientManager(): OAuth2AuthorizedClientManager = mock()

    @Bean
    fun oidcTokenRefreshFilter(
        authorizedClientManager: OAuth2AuthorizedClientManager,
        authorizedClientRepository: OAuth2AuthorizedClientRepository,
    ): OidcTokenRefreshFilter = OidcTokenRefreshFilter(authorizedClientManager, authorizedClientRepository)
}
