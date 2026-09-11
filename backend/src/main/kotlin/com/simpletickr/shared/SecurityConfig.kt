package com.simpletickr.shared

import com.simpletickr.auth.oidc.LazyOidcClientRegistrationRepository
import com.simpletickr.auth.oidc.OidcAuthenticationFailureHandler
import com.simpletickr.auth.oidc.OidcAuthenticationSuccessHandler
import com.simpletickr.auth.oidc.OidcSettings
import com.simpletickr.auth.oidc.OidcTokenRefreshFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${cors.allowed-origins}") private val allowedOrigins: String,
    private val oidcSettings: OidcSettings,
    private val oidcClientRegistrationRepository: LazyOidcClientRegistrationRepository,
    private val oidcAuthenticationSuccessHandler: OidcAuthenticationSuccessHandler,
    private val oidcAuthenticationFailureHandler: OidcAuthenticationFailureHandler,
    private val oidcTokenRefreshFilter: OidcTokenRefreshFilter,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = allowedOrigins.split(",").map(String::trim)
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

    @Bean
    fun securityFilterChain(http: HttpSecurity, corsConfigurationSource: CorsConfigurationSource): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                it.requestMatchers(HttpMethod.GET, "/auth/config").permitAll()
                it.requestMatchers(HttpMethod.GET, "/health", "/actuator/health", "/actuator/health/**").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json"
                    response.writer.write("""{"message":"Authentication required"}""")
                }
            }

        // Config-presence is the toggle: with no OIDC_ISSUER_URI/CLIENT_ID/CLIENT_SECRET set,
        // zero OIDC filters are registered at all and local auth is completely unaffected.
        if (oidcSettings.enabled) {
            http.addFilterAfter(oidcTokenRefreshFilter, SecurityContextHolderFilter::class.java)
            http.oauth2Login { oauth2 ->
                oauth2.authorizationEndpoint { it.authorizationRequestResolver(pkceAuthorizationRequestResolver()) }
                oauth2.successHandler(oidcAuthenticationSuccessHandler)
                oauth2.failureHandler(oidcAuthenticationFailureHandler)
            }
        }

        return http.build()
    }

    // The issue calls for Authorization Code + PKCE explicitly; Spring's client-side default
    // does not enable PKCE for confidential clients automatically, so it's opted in here.
    private fun pkceAuthorizationRequestResolver(): OAuth2AuthorizationRequestResolver {
        val resolver = DefaultOAuth2AuthorizationRequestResolver(oidcClientRegistrationRepository, "/oauth2/authorization")
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
        return resolver
    }
}
