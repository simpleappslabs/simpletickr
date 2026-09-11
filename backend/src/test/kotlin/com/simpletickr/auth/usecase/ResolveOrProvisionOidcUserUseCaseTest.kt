package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.user.model.User
import com.simpletickr.user.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class ResolveOrProvisionOidcUserUseCaseTest {

    private val identityRepository = mock<IdentityRepository>()
    private val userRepository = mock<UserRepository>()
    private val useCase = ResolveOrProvisionOidcUserUseCase(identityRepository, userRepository)

    private val claims = OidcClaims(
        providerId = "https://idp.example.com",
        subject = "sub-123",
        preferredUsername = "alice",
        email = "alice@example.com",
    )

    @Test
    fun `returns the existing user when the identity is already known`() {
        whenever(identityRepository.findByProviderIdAndSubject("https://idp.example.com", "sub-123")).thenReturn(
            Identity(id = 5L, userId = 42L, providerType = ProviderType.OIDC, providerId = "https://idp.example.com", subject = "sub-123", passwordHash = null)
        )
        whenever(userRepository.findById(42L)).thenReturn(User(42L, "alice"))

        val result = useCase.execute(claims)

        assertEquals(User(42L, "alice"), result)
        verify(userRepository, org.mockito.kotlin.never()).save(any())
    }

    @Test
    fun `auto-provisions a new user from preferredUsername when the identity is unknown`() {
        whenever(identityRepository.findByProviderIdAndSubject(any(), any())).thenReturn(null)
        whenever(userRepository.findByUsername("alice")).thenReturn(null)
        whenever(userRepository.save("alice")).thenReturn(User(7L, "alice"))

        val result = useCase.execute(claims)

        assertEquals(User(7L, "alice"), result)
        verify(identityRepository).save(
            Identity(id = 0L, userId = 7L, providerType = ProviderType.OIDC, providerId = "https://idp.example.com", subject = "sub-123", passwordHash = null)
        )
    }

    @Test
    fun `falls back to the email local-part when preferredUsername is absent`() {
        val emailOnlyClaims = claims.copy(preferredUsername = null)
        whenever(identityRepository.findByProviderIdAndSubject(any(), any())).thenReturn(null)
        whenever(userRepository.findByUsername("alice")).thenReturn(null)
        whenever(userRepository.save("alice")).thenReturn(User(7L, "alice"))

        useCase.execute(emailOnlyClaims)

        verify(userRepository).save("alice")
    }

    @Test
    fun `falls back to the subject when preferredUsername and email are absent`() {
        val subjectOnlyClaims = claims.copy(preferredUsername = null, email = null)
        whenever(identityRepository.findByProviderIdAndSubject(any(), any())).thenReturn(null)
        whenever(userRepository.findByUsername("sub-123")).thenReturn(null)
        whenever(userRepository.save("sub-123")).thenReturn(User(7L, "sub-123"))

        useCase.execute(subjectOnlyClaims)

        verify(userRepository).save("sub-123")
    }

    @Test
    fun `suffixes the username on collision until a free one is found`() {
        whenever(identityRepository.findByProviderIdAndSubject(any(), any())).thenReturn(null)
        whenever(userRepository.findByUsername("alice")).thenReturn(User(1L, "alice"))
        whenever(userRepository.findByUsername("alice-2")).thenReturn(User(2L, "alice-2"))
        whenever(userRepository.findByUsername("alice-3")).thenReturn(null)
        whenever(userRepository.save("alice-3")).thenReturn(User(7L, "alice-3"))

        val result = useCase.execute(claims)

        assertEquals(User(7L, "alice-3"), result)
    }

    @Test
    fun `does not create an Organization or Membership row`() {
        whenever(identityRepository.findByProviderIdAndSubject(any(), any())).thenReturn(null)
        whenever(userRepository.findByUsername(any())).thenReturn(null)
        whenever(userRepository.save(any())).thenReturn(User(7L, "alice"))

        useCase.execute(claims)

        verify(identityRepository).save(any())
        verify(userRepository).save(eq("alice"))
    }
}
