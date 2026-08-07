package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.user.model.User
import com.simpletickr.user.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BootstrapAdminUseCaseTest {

    private val identityRepository = mock<IdentityRepository>()
    private val userRepository = mock<UserRepository>()
    private val passwordEncoder = mock<PasswordEncoder>()
    private val useCase = BootstrapAdminUseCase(identityRepository, userRepository, passwordEncoder)

    @Test
    fun `execute does nothing when identities already exist`() {
        whenever(identityRepository.count()).thenReturn(1L)

        assertNull(useCase.execute())
        verify(userRepository, never()).findAll()
    }

    @Test
    fun `execute renames the pre-existing user to admin and attaches a local identity`() {
        whenever(identityRepository.count()).thenReturn(0L)
        whenever(userRepository.findAll()).thenReturn(listOf(User(1L, "default")))
        whenever(passwordEncoder.encode(any())).thenReturn("hashed")

        val result = useCase.execute()

        assertEquals("admin", result?.username)
        verify(userRepository).updateUsername(1L, "admin")
        verify(identityRepository).save(
            Identity(
                id = 0L, userId = 1L, providerType = ProviderType.LOCAL,
                providerId = Identity.LOCAL_PROVIDER_ID, subject = null, passwordHash = "hashed",
            )
        )
    }

    @Test
    fun `execute generates a random password and returns it in plaintext`() {
        whenever(identityRepository.count()).thenReturn(0L)
        whenever(userRepository.findAll()).thenReturn(listOf(User(1L, "default")))
        whenever(passwordEncoder.encode(any())).thenReturn("hashed")

        val result = useCase.execute()

        assertEquals(20, result?.password?.length)
    }
}
