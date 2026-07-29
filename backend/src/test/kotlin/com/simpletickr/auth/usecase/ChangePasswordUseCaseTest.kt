package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertFailsWith

class ChangePasswordUseCaseTest {

    private val identityRepository = mock<IdentityRepository>()
    private val passwordEncoder = mock<PasswordEncoder>()
    private val useCase = ChangePasswordUseCase(identityRepository, passwordEncoder)

    private val identity = Identity(
        id = 1L, userId = 10L, providerType = ProviderType.LOCAL,
        providerId = Identity.LOCAL_PROVIDER_ID, subject = null, passwordHash = "old-hash",
    )

    @Test
    fun `execute throws when user has no local identity`() {
        whenever(identityRepository.findByUserIdAndProviderType(10L, ProviderType.LOCAL)).thenReturn(null)

        assertFailsWith<IllegalArgumentException> { useCase.execute(10L, "current", "new") }
    }

    @Test
    fun `execute throws when current password is incorrect`() {
        whenever(identityRepository.findByUserIdAndProviderType(10L, ProviderType.LOCAL)).thenReturn(identity)
        whenever(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false)

        assertFailsWith<IllegalArgumentException> { useCase.execute(10L, "wrong", "new") }
        verify(identityRepository, org.mockito.kotlin.never()).updatePasswordHash(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `execute hashes and stores the new password when current password matches`() {
        whenever(identityRepository.findByUserIdAndProviderType(10L, ProviderType.LOCAL)).thenReturn(identity)
        whenever(passwordEncoder.matches("current", "old-hash")).thenReturn(true)
        whenever(passwordEncoder.encode("new-password")).thenReturn("new-hash")

        useCase.execute(10L, "current", "new-password")

        verify(identityRepository).updatePasswordHash(1L, "new-hash")
    }
}
