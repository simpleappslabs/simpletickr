package com.simpletickr.auth.oidc

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository

/**
 * Overrides Spring Boot's default in-memory OAuth2AuthorizedClientRepository. We want the
 * access/refresh token tied to the HTTP session — already persisted to Postgres via Spring
 * Session JDBC — not an in-memory map that's lost on restart and doesn't work across
 * horizontally-scaled replicas.
 */
@Configuration
class OidcAuthorizedClientConfig {

    @Bean
    fun authorizedClientRepository(): OAuth2AuthorizedClientRepository = HttpSessionOAuth2AuthorizedClientRepository()

    @Bean
    fun oidcAuthorizedClientManager(
        clientRegistrationRepository: LazyOidcClientRegistrationRepository,
        authorizedClientRepository: OAuth2AuthorizedClientRepository,
    ): OAuth2AuthorizedClientManager =
        DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository).apply {
            setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build()
            )
        }
}
