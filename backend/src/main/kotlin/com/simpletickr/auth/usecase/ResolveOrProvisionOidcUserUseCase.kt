package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.user.model.User
import com.simpletickr.user.persistence.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Per ADR-0002, OIDC auto-provisioning is the multi-user mechanism itself, not a secondary path —
 * a login from an unrecognized (provider_id, subject) always creates a new User. The IdP is the
 * trust boundary; simpletickr does not gate this behind an allowlist or admin approval.
 */
@Service
class ResolveOrProvisionOidcUserUseCase(
    private val identityRepository: IdentityRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun execute(claims: OidcClaims): User {
        val existing = identityRepository.findByProviderIdAndSubject(claims.providerId, claims.subject)
        if (existing != null) {
            return userRepository.findById(existing.userId)
                ?: error("Identity ${existing.id} references missing user ${existing.userId}")
        }

        val username = uniqueUsernameFor(claims)
        val user = userRepository.save(username)
        identityRepository.save(
            Identity(
                id = 0L,
                userId = user.id,
                providerType = ProviderType.OIDC,
                providerId = claims.providerId,
                subject = claims.subject,
                passwordHash = null,
            )
        )
        return user
    }

    private fun uniqueUsernameFor(claims: OidcClaims): String {
        val base = claims.preferredUsername
            ?: claims.email?.substringBefore("@")
            ?: claims.subject

        if (userRepository.findByUsername(base) == null) return base

        var suffix = 2
        while (userRepository.findByUsername("$base-$suffix") != null) suffix++
        return "$base-$suffix"
    }
}
