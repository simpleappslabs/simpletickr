package com.simpletickr.auth.persistence

import com.simpletickr.auth.model.MembershipRole
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertTrue

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(MembershipRepository::class, OrganizationRepository::class)
class MembershipRepositoryIT {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:17")
    }

    @Autowired
    private lateinit var repository: MembershipRepository

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    private var organizationId: Long = 0

    @BeforeEach
    fun setup() {
        organizationId = organizationRepository.save("Test Org").id
    }

    @Test
    fun `save creates a membership and returns it with a generated id`() {
        val membership = repository.save(1L, organizationId, MembershipRole.OWNER)
        assertTrue(membership.id > 0)
        assertEquals(1L, membership.userId)
        assertEquals(organizationId, membership.organizationId)
        assertEquals(MembershipRole.OWNER, membership.role)
    }
}
