package com.simpletickr.auth

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun url(path: String) = "http://localhost:$port$path"

    @BeforeEach
    fun seedLocalIdentity() {
        // Bootstrap already ran at context startup and created 'admin'/<random password> for the
        // pre-existing seeded 'default' user (id=1) has no local identity yet, so give it one
        // with a password we control for this test.
        jdbcTemplate.update(
            "DELETE FROM identities WHERE user_id = 1 AND provider_type = 'LOCAL'"
        )
        jdbcTemplate.update(
            "INSERT INTO identities (user_id, provider_type, provider_id, password_hash) VALUES (1, 'LOCAL', 'local', ?)",
            // argon2id hash of "TestPassword123!"
            "\$argon2id\$v=19\$m=16384,t=2,p=1\$TgJcP7fYvua2e3e6gV2LeQ\$IAIbjufynaHyjPbFjS0WNrIYVXt6cB+Cg5YbFp0prVE"
        )
    }

    @Test
    fun `protected endpoint rejects unauthenticated requests`() {
        val response = restTemplate.getForEntity(url("/portfolios"), String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `login then reusing the session cookie authenticates subsequent requests, logout clears it`() {
        val loginResponse = restTemplate.postForEntity(
            url("/auth/login"),
            HttpEntity(mapOf("username" to "default", "password" to "TestPassword123!"), jsonHeaders()),
            String::class.java,
        )
        assertEquals(HttpStatus.OK, loginResponse.statusCode)
        val cookie = loginResponse.headers.getFirst(HttpHeaders.SET_COOKIE)
        assertNotNull(cookie)
        assertTrue(cookie.contains("SESSION"))

        val sessionCookie = cookie.substringBefore(";")
        val authedHeaders = HttpHeaders().apply { add(HttpHeaders.COOKIE, sessionCookie) }

        val meResponse = restTemplate.exchange(
            url("/auth/me"), HttpMethod.GET, HttpEntity<Void>(authedHeaders), String::class.java,
        )
        assertEquals(HttpStatus.OK, meResponse.statusCode)
        assertTrue(meResponse.body?.contains("default") == true)

        val portfoliosResponse = restTemplate.exchange(
            url("/portfolios"), HttpMethod.GET, HttpEntity<Void>(authedHeaders), String::class.java,
        )
        assertEquals(HttpStatus.OK, portfoliosResponse.statusCode)

        val logoutResponse = restTemplate.exchange(
            url("/auth/logout"), HttpMethod.POST, HttpEntity<Void>(authedHeaders), String::class.java,
        )
        assertEquals(HttpStatus.NO_CONTENT, logoutResponse.statusCode)

        val afterLogoutResponse = restTemplate.exchange(
            url("/auth/me"), HttpMethod.GET, HttpEntity<Void>(authedHeaders), String::class.java,
        )
        assertEquals(HttpStatus.UNAUTHORIZED, afterLogoutResponse.statusCode)
    }

    @Test
    fun `login with wrong password returns 401`() {
        val response = restTemplate.postForEntity(
            url("/auth/login"),
            HttpEntity(mapOf("username" to "default", "password" to "wrong-password"), jsonHeaders()),
            String::class.java,
        )
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun jsonHeaders() = HttpHeaders().apply { set(HttpHeaders.CONTENT_TYPE, "application/json") }
}
