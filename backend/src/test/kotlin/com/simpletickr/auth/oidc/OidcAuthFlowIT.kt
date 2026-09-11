package com.simpletickr.auth.oidc

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives the OIDC Authorization Code + PKCE flow end to end against a WireMock-stubbed IdP,
 * extending AuthFlowIT's Testcontainers-Postgres pattern. No browser is involved: redirects are
 * followed manually, thread by thread, exactly like a browser would, capturing the state/nonce
 * from the first redirect and the session cookie between hops.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OidcAuthFlowIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")

        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun startWireMock() {
            wireMock.start()
        }

        @JvmStatic
        @AfterAll
        fun stopWireMock() {
            wireMock.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun oidcProperties(registry: DynamicPropertyRegistry) {
            registry.add("oidc.issuer-uri") { wireMock.baseUrl() }
            registry.add("oidc.client-id") { "test-client" }
            registry.add("oidc.client-secret") { "test-secret" }
            registry.add("frontend.base-url") { "http://frontend.test" }
        }
    }

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // TestRestTemplate follows redirects by default — this flow needs to inspect each hop.
        restTemplate.restTemplate.requestFactory = NoRedirectRequestFactory()

        wireMock.resetAll()
        wireMock.stubFor(
            get(urlEqualTo("/.well-known/openid-configuration")).willReturn(
                okJson(
                    """
                    {
                      "issuer": "${wireMock.baseUrl()}",
                      "authorization_endpoint": "${wireMock.baseUrl()}/authorize",
                      "token_endpoint": "${wireMock.baseUrl()}/token",
                      "jwks_uri": "${wireMock.baseUrl()}/jwks",
                      "response_types_supported": ["code"],
                      "subject_types_supported": ["public"],
                      "id_token_signing_alg_values_supported": ["RS256"]
                    }
                    """.trimIndent()
                )
            )
        )
        wireMock.stubFor(get(urlEqualTo("/jwks")).willReturn(okJson(JWKSet(publicJwk()).toString())))
    }

    @Test
    fun `fresh OIDC login auto-provisions a new user`() {
        val (state, nonce, preAuthCookie) = startAuthorizationRequest()
        stubTokenEndpoint(idToken(nonce, subject = "sub-alice", preferredUsername = "alice", email = "alice@example.com"))

        val callback = callback(state, preAuthCookie)
        assertEquals(HttpStatus.FOUND, callback.statusCode)
        assertEquals("http://frontend.test/", callback.headers.location.toString())

        val sessionCookie = cookieFrom(callback)!!
        val me = getWithCookie("/auth/me", sessionCookie)
        assertEquals(HttpStatus.OK, me.statusCode)
        assertTrue(me.body?.contains("\"username\":\"alice\"") == true)

        val userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = 'alice'", Long::class.java
        )
        assertEquals(1L, userCount)
    }

    @Test
    fun `repeat login resolves the same auto-provisioned user rather than creating a duplicate`() {
        val (state1, nonce1, cookie1) = startAuthorizationRequest()
        stubTokenEndpoint(idToken(nonce1, subject = "sub-carol", preferredUsername = "carol", email = "carol@example.com"))
        val firstLogin = callback(state1, cookie1)
        val firstUserId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'carol'", Long::class.java
        )

        val (state2, nonce2, cookie2) = startAuthorizationRequest()
        stubTokenEndpoint(idToken(nonce2, subject = "sub-carol", preferredUsername = "carol", email = "carol@example.com"))
        callback(state2, cookie2)

        val userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = 'carol'", Long::class.java
        )
        assertEquals(1L, userCount)
        val secondUserId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'carol'", Long::class.java
        )
        assertEquals(firstUserId, secondUserId)
        assertNotNull(firstLogin.headers.location)
    }

    @Test
    fun `connect flow links a new OIDC identity to the already logged-in local user`() {
        val localCookie = loginAsLocalAdmin()

        val (state, nonce, connectCookie) = startConnectAuthorizationRequest(localCookie)
        stubTokenEndpoint(idToken(nonce, subject = "sub-bob", preferredUsername = "bob", email = "bob@example.com"))

        val callback = callback(state, connectCookie)
        assertEquals(HttpStatus.FOUND, callback.statusCode)
        assertEquals("http://frontend.test/settings/security?oidcConnected=true", callback.headers.location.toString())

        val linkedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identities WHERE user_id = 1 AND provider_type = 'OIDC' AND subject = 'sub-bob'",
            Long::class.java,
        )
        assertEquals(1L, linkedCount)

        // Connecting must not replace the session's identity — it's still the local admin.
        val sessionCookie = cookieFrom(callback)!!
        val me = getWithCookie("/auth/me", sessionCookie)
        assertTrue(me.body?.contains("\"username\":\"admin\"") == true)
        assertTrue(me.body?.contains("\"oidcLinked\":true") == true)
    }

    @Test
    fun `connect rejects a subject already owned by a different user`() {
        jdbcTemplate.update("INSERT INTO users (username) VALUES ('dave')")
        val daveId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = 'dave'", Long::class.java)!!
        jdbcTemplate.update(
            "INSERT INTO identities (user_id, provider_type, provider_id, subject) VALUES (?, 'OIDC', ?, 'sub-taken')",
            daveId, wireMock.baseUrl(),
        )

        val localCookie = loginAsLocalAdmin()
        val (state, nonce, connectCookie) = startConnectAuthorizationRequest(localCookie)
        stubTokenEndpoint(idToken(nonce, subject = "sub-taken", preferredUsername = "someone-else", email = null))

        val callback = callback(state, connectCookie)
        assertEquals(HttpStatus.FOUND, callback.statusCode)
        assertEquals(
            "http://frontend.test/settings/security?oidcError=already-linked",
            callback.headers.location.toString(),
        )

        val stillDavesCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identities WHERE user_id = ? AND subject = 'sub-taken'", Long::class.java, daveId
        )
        assertEquals(1L, stillDavesCount)

        // The rejected connect must not leave the session on Spring's raw OIDC principal —
        // it has to still be usable as the local admin who was already logged in.
        val sessionCookie = cookieFrom(callback)!!
        val me = getWithCookie("/auth/me", sessionCookie)
        assertEquals(HttpStatus.OK, me.statusCode)
        assertTrue(me.body?.contains("\"username\":\"admin\"") == true)
    }

    // --- flow helpers -------------------------------------------------------------------

    private data class AuthorizationStart(val state: String, val nonce: String?, val cookie: String)

    private fun startAuthorizationRequest(): AuthorizationStart {
        val response = restTemplate.getForEntity("/oauth2/authorization/oidc", String::class.java)
        assertEquals(HttpStatus.FOUND, response.statusCode)
        val location = URI(response.headers.location.toString())
        val params = parseQueryParams(location)
        return AuthorizationStart(params.getValue("state"), params["nonce"], cookieFrom(response)!!)
    }

    private fun startConnectAuthorizationRequest(localCookie: String): AuthorizationStart {
        val connectStart = getWithCookie("/auth/oidc/connect", localCookie)
        assertEquals(HttpStatus.FOUND, connectStart.statusCode)
        assertTrue(connectStart.headers.location.toString().contains("/oauth2/authorization/oidc"))
        val connectCookie = cookieFrom(connectStart) ?: localCookie

        val authorizeResponse = getWithCookie("/oauth2/authorization/oidc", connectCookie)
        assertEquals(HttpStatus.FOUND, authorizeResponse.statusCode)
        val location = URI(authorizeResponse.headers.location.toString())
        val params = parseQueryParams(location)
        return AuthorizationStart(params.getValue("state"), params["nonce"], cookieFrom(authorizeResponse) ?: connectCookie)
    }

    private fun loginAsLocalAdmin(): String {
        jdbcTemplate.update("DELETE FROM identities WHERE user_id = 1 AND provider_type = 'LOCAL'")
        jdbcTemplate.update(
            "INSERT INTO identities (user_id, provider_type, provider_id, password_hash) VALUES (1, 'LOCAL', 'local', ?)",
            // argon2id hash of "TestPassword123!"
            "\$argon2id\$v=19\$m=16384,t=2,p=1\$TgJcP7fYvua2e3e6gV2LeQ\$IAIbjufynaHyjPbFjS0WNrIYVXt6cB+Cg5YbFp0prVE"
        )
        val loginResponse = restTemplate.postForEntity(
            "/auth/login",
            HttpEntity(mapOf("username" to "admin", "password" to "TestPassword123!"), jsonHeaders()),
            String::class.java,
        )
        assertEquals(HttpStatus.OK, loginResponse.statusCode)
        return cookieFrom(loginResponse)!!
    }

    private fun callback(state: String, cookie: String) =
        getWithCookie("/login/oauth2/code/oidc?code=test-code&state=$state", cookie)

    private fun getWithCookie(path: String, cookie: String) =
        restTemplate.exchange(
            path, HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { add(HttpHeaders.COOKIE, cookie) }),
            String::class.java,
        )

    private fun cookieFrom(response: org.springframework.http.ResponseEntity<String>) =
        response.headers.getFirst(HttpHeaders.SET_COOKIE)?.substringBefore(";")

    private fun jsonHeaders() = HttpHeaders().apply { set(HttpHeaders.CONTENT_TYPE, "application/json") }

    private fun parseQueryParams(uri: URI): Map<String, String> =
        (uri.rawQuery ?: "").split("&").filter { it.isNotBlank() }.associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    private fun stubTokenEndpoint(signedIdToken: String) {
        wireMock.stubFor(
            post(urlEqualTo("/token")).willReturn(
                okJson(
                    """
                    {
                      "access_token": "test-access-token",
                      "token_type": "Bearer",
                      "expires_in": 3600,
                      "refresh_token": "test-refresh-token",
                      "id_token": "$signedIdToken",
                      "scope": "openid profile email"
                    }
                    """.trimIndent()
                )
            )
        )
    }

    private fun publicJwk(): RSAKey =
        RSAKey.Builder(keyPair.public as RSAPublicKey).keyID("test-key").build()

    private fun idToken(nonce: String?, subject: String, preferredUsername: String?, email: String?): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(wireMock.baseUrl())
            .subject(subject)
            .audience("test-client")
            .issueTime(Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .apply {
                nonce?.let { claim("nonce", it) }
                preferredUsername?.let { claim("preferred_username", it) }
                email?.let { claim("email", it) }
            }
            .build()

        val signedJwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims)
        signedJwt.sign(RSASSASigner(keyPair.private as RSAPrivateKey))
        return signedJwt.serialize()
    }

    // TestRestTemplate follows redirects by default; this flow needs to inspect (and choose
    // whether to follow) each hop itself, so redirect-following is disabled at the connection level.
    private class NoRedirectRequestFactory : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: java.net.HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }
    }
}
