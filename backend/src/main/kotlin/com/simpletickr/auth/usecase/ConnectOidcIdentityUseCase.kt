package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class IdentityAlreadyLinkedException(message: String) : RuntimeException(message)

/**
 * Self-service, one-directional: a logged-in local user adds SSO to their own account. Never
 * auto-links by email or any other means — a subject already owned by a different user is
 * rejected outright, consistent with ADR-0002.
 */
@Service
class ConnectOidcIdentityUseCase(
    private val identityRepository: IdentityRepository,
) {

    @Transactional
    fun execute(currentUserId: Long, claims: OidcClaims) {
        val existing = identityRepository.findByProviderIdAndSubject(claims.providerId, claims.subject)
        if (existing != null) {
            if (existing.userId != currentUserId) {
                throw IdentityAlreadyLinkedException(
                    "This identity is already linked to a different account"
                )
            }
            return
        }

        identityRepository.save(
            Identity(
                id = 0L,
                userId = currentUserId,
                providerType = ProviderType.OIDC,
                providerId = claims.providerId,
                subject = claims.subject,
                passwordHash = null,
            )
        )
    }
}
