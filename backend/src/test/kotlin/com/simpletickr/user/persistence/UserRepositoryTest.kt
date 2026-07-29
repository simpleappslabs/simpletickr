package com.simpletickr.user.persistence

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(UserRepository::class)
class UserRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: UserRepository

    @Test
    fun `findById returns the seeded default user`() {
        val found = repository.findById(1L)
        assertNotNull(found)
        assertEquals("default", found.username)
    }

    @Test
    fun `findById returns null when user does not exist`() {
        assertNull(repository.findById(-1L))
    }

    @Test
    fun `findByUsername returns null when no such user`() {
        assertNull(repository.findByUsername("nobody"))
    }

    @Test
    fun `save creates a user and returns it with a generated id`() {
        val user = repository.save("newuser")
        assertNotNull(user)
        assertEquals("newuser", user.username)

        val found = repository.findByUsername("newuser")
        assertNotNull(found)
        assertEquals(user.id, found.id)
    }
}
