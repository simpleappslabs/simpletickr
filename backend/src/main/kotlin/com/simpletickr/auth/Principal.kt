package com.simpletickr.auth

import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import java.io.Serializable

/**
 * The generated controller interfaces (openapi-generator, interfaceOnly) have fixed method
 * signatures with no room for an injected `@AuthenticationPrincipal` parameter, so controllers
 * read the principal directly off the (thread-bound) SecurityContext instead.
 */
fun currentUser(): Principal =
    SecurityContextHolder.getContext().authentication.principal as Principal

// Serializable: Spring Session JDBC persists the SecurityContext (and this principal within
// it) to Postgres via Java serialization.
// AuthenticatedPrincipal: without it, Authentication.getName() (used by Spring Session to
// populate SPRING_SESSION.PRINCIPAL_NAME, a VARCHAR(100)) falls back to this data class's
// auto-generated toString() — long enough with a real OIDC subject/issuer to overflow that
// column. This is the same interface OidcUser itself implements for the same reason.
sealed interface Principal : Serializable, AuthenticatedPrincipal {
    val id: Long
    val username: String

    override fun getName(): String = username

    data class Local(override val id: Long, override val username: String) : Principal

    data class Oidc(
        override val id: Long,
        override val username: String,
        val providerId: String,
        val subject: String,
    ) : Principal
}
