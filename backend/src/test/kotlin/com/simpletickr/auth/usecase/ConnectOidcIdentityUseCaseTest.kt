package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

class ConnectOidcIdentityUseCaseTest {

    private val identityRepository = mock<IdentityRepository>()
    private val useCase = ConnectOidcIdentityUseCase(identityRepository)

    private val claims = OidcClaims(
        providerId = "https://idp.example.com",
        subject = "sub-123",
        preferredUsername = "alice",
        email = "alice@example.com",
    )

    @Test
    fun `links a new OIDC identity to the current user when the subject is unknown`() {
        whenever(identityRepository.findByProviderIdAndSubject("https://idp.example.com", "sub-123")).thenReturn(null)

        useCase.execute(currentUserId = 1L, claims = claims)

        verify(identityRepository).save(
            Identity(id = 0L, userId = 1L, providerType = ProviderType.OIDC, providerId = "https://idp.example.com", subject = "sub-123", passwordHash = null)
        )
    }

    @Test
    fun `is a no-op when the subject is already linked to the same user`() {
        whenever(identityRepository.findByProviderIdAndSubject(any(), any())).thenReturn(
            Identity(id = 9L, userId = 1L, providerType = ProviderType.OIDC, providerId = "https://idp.example.com", subject = "sub-123", passwordHash = null)
        )

        useCase.execute(currentUserId = 1L, claims = claims)

        verify(identityRepository, never()).save(any())
    }

    @Test
    fun `rejects linking a subject already owned by a different user`() {
        whenever(identityRepository.findByProviderIdAndSubject(any(), any())).thenReturn(
            Identity(id = 9L, userId = 2L, providerType = ProviderType.OIDC, providerId = "https://idp.example.com", subject = "sub-123", passwordHash = null)
        )

        assertFailsWith<IdentityAlreadyLinkedException> {
            useCase.execute(currentUserId = 1L, claims = claims)
        }
        verify(identityRepository, never()).save(any())
    }
}
