package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class ChangePasswordUseCase(
    private val identityRepository: IdentityRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun execute(userId: Long, currentPassword: String, newPassword: String) {
        val identity = identityRepository.findByUserIdAndProviderType(userId, ProviderType.LOCAL)
            ?: throw IllegalArgumentException("No local identity for this user")
        require(passwordEncoder.matches(currentPassword, identity.passwordHash)) {
            "Current password is incorrect"
        }
        identityRepository.updatePasswordHash(identity.id, passwordEncoder.encode(newPassword))
    }
}
