package com.simpletickr.account

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import com.simpletickr.account.persistence.AccountRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AccountRepository::class)
class AccountRepositoryIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: AccountRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun newAccount(userId: Long, name: String) = Account(
        id = 0L, userId = userId, name = name, broker = null, accountType = AccountType.BROKERAGE,
        currency = null, accountNumber = null, institution = null,
    )

    private fun otherUserId(): Long =
        jdbcTemplate.queryForObject("INSERT INTO users (username) VALUES ('other') RETURNING id", Long::class.java)!!

    @Test
    fun `isOwnedBy returns true when the account belongs to that user`() {
        val account = repository.save(newAccount(1L, "Test"))
        assertTrue(repository.isOwnedBy(account.id, 1L))
    }

    @Test
    fun `isOwnedBy returns false when the account belongs to a different user`() {
        val otherId = otherUserId()
        val account = repository.save(newAccount(otherId, "Someone else's account"))
        assertFalse(repository.isOwnedBy(account.id, 1L))
    }

    @Test
    fun `isOwnedBy returns false when the account does not exist`() {
        assertFalse(repository.isOwnedBy(-1L, 1L))
    }

    @Test
    fun `save creates an account and returns it with a generated id`() {
        val account = repository.save(newAccount(1L, "My Brokerage"))
        assertTrue(account.id > 0)
        assertEquals("My Brokerage", account.name)
    }

    @Test
    fun `findAllForUser only returns accounts owned by that user`() {
        // User 1 already owns the base migrations' seeded "Default" account, so assert on
        // presence/absence rather than an exact count.
        val otherId = otherUserId()
        repository.save(newAccount(1L, "Mine"))
        repository.save(newAccount(otherId, "Not mine"))

        val result = repository.findAllForUser(1L)

        assertTrue(result.any { it.name == "Mine" })
        assertTrue(result.none { it.name == "Not mine" })
    }

    @Test
    fun `delete removes the account`() {
        val saved = repository.save(newAccount(1L, "To Delete"))
        repository.delete(saved.id)
        assertNull(repository.findById(saved.id))
    }
}
