package com.simpletickr.auth

import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import kotlin.test.assertEquals

/**
 * Regression coverage for a real bug: Spring Session JDBC populates SPRING_SESSION.PRINCIPAL_NAME
 * (VARCHAR(100)) from Authentication.getName(), whose default implementation falls back to
 * principal.toString() for any principal that isn't a UserDetails/AuthenticatedPrincipal/
 * java.security.Principal. A plain data class's toString() — id, username, providerId, subject
 * all included — comfortably exceeds 100 chars with a real OIDC subject, overflowing the column.
 * Every test elsewhere used short fixture data and never caught this; only a real IdP did.
 */
class PrincipalTest {

    @Test
    fun `Authentication name for a local principal is just the username, not a data class dump`() {
        val principal = Principal.Local(id = 1L, username = "admin")

        val name = UsernamePasswordAuthenticationToken(principal, null, emptyList()).name

        assertEquals("admin", name)
    }

    @Test
    fun `Authentication name for an oidc principal is just the username, even with a long subject and issuer`() {
        val principal = Principal.Oidc(
            id = 4L,
            username = "dev-sso",
            providerId = "https://idp.example.com/some/long/issuer/path",
            subject = "969ec434-f30d-4400-a35c-d597b257e64a",
        )

        val name = UsernamePasswordAuthenticationToken(principal, null, emptyList()).name

        assertEquals("dev-sso", name)
    }
}
