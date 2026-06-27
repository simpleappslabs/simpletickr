package com.simpletickr.portfolio

import com.simpletickr.portfolio.persistence.PortfolioRepository
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
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PortfolioRepository::class)
class PortfolioRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: PortfolioRepository

    @Test
    fun `findAll returns empty list when no portfolios exist`() {
        val result = repository.findAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `save creates a portfolio and returns it with a generated id`() {
        val portfolio = repository.save("My Portfolio")
        assertTrue(portfolio.id > 0)
        assertEquals("My Portfolio", portfolio.name)
    }

    @Test
    fun `findById returns the portfolio when it exists`() {
        val saved = repository.save("Test Portfolio")
        val found = repository.findById(saved.id)
        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("Test Portfolio", found.name)
    }

    @Test
    fun `findById returns null when portfolio does not exist`() {
        assertNull(repository.findById(-1L))
    }

    @Test
    fun `update changes the portfolio name and returns the updated portfolio`() {
        val saved = repository.save("Original Name")
        val updated = repository.update(saved.id, "Updated Name")
        assertNotNull(updated)
        assertEquals(saved.id, updated.id)
        assertEquals("Updated Name", updated.name)
    }

    @Test
    fun `update returns null when portfolio does not exist`() {
        assertNull(repository.update(-1L, "Whatever"))
    }

    @Test
    fun `delete removes the portfolio`() {
        val saved = repository.save("To Delete")
        repository.delete(saved.id)
        assertNull(repository.findById(saved.id))
    }
}