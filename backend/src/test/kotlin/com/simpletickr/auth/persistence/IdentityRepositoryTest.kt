package com.simpletickr.auth.persistence

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
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
@Import(IdentityRepository::class)
class IdentityRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: IdentityRepository

    @Test
    fun `count is zero when no identities exist`() {
        assertEquals(0L, repository.count())
    }

    @Test
    fun `save creates an identity and count reflects it`() {
        repository.save(
            Identity(id = 0L, userId = 1L, providerType = ProviderType.LOCAL, providerId = Identity.LOCAL_PROVIDER_ID, subject = null, passwordHash = "hash")
        )
        assertEquals(1L, repository.count())
    }

    @Test
    fun `findByUserIdAndProviderType returns the saved identity`() {
        val saved = repository.save(
            Identity(id = 0L, userId = 1L, providerType = ProviderType.LOCAL, providerId = Identity.LOCAL_PROVIDER_ID, subject = null, passwordHash = "hash")
        )

        val found = repository.findByUserIdAndProviderType(1L, ProviderType.LOCAL)

        assertNotNull(found)
        assertEquals(saved.id, found.id)
        assertEquals("hash", found.passwordHash)
    }

    @Test
    fun `findByUserIdAndProviderType returns null when no matching identity`() {
        assertNull(repository.findByUserIdAndProviderType(1L, ProviderType.LOCAL))
    }

    @Test
    fun `updatePasswordHash changes the stored hash`() {
        val saved = repository.save(
            Identity(id = 0L, userId = 1L, providerType = ProviderType.LOCAL, providerId = Identity.LOCAL_PROVIDER_ID, subject = null, passwordHash = "old")
        )

        repository.updatePasswordHash(saved.id, "new")

        val found = repository.findByUserIdAndProviderType(1L, ProviderType.LOCAL)
        assertEquals("new", found?.passwordHash)
    }
}
