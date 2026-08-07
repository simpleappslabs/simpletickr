package com.simpletickr.auth.usecase

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.user.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom

data class BootstrapResult(val username: String, val password: String)

@Service
class BootstrapAdminUseCase(
    private val identityRepository: IdentityRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val passwordAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    // Every deploy already has exactly one user by this point (the "default" user seeded by V4,
    // with its org/membership backfilled by V30) — attach to it rather than create a new one.
    @Transactional
    fun execute(): BootstrapResult? {
        if (identityRepository.count() > 0) return null

        log.info("No identities found — bootstrapping admin user")
        val username = "admin"
        val password = generatePassword()

        val users = userRepository.findAll()
        check(users.size == 1) {
            "Expected exactly one pre-existing user to bootstrap admin onto, found ${users.size} " +
                "(ids: ${users.map { it.id }}) — refusing to guess which one should become admin"
        }
        val user = users.single()
        userRepository.updateUsername(user.id, username)
        identityRepository.save(
            Identity(
                id = 0L,
                userId = user.id,
                providerType = ProviderType.LOCAL,
                providerId = Identity.LOCAL_PROVIDER_ID,
                subject = null,
                passwordHash = passwordEncoder.encode(password),
            )
        )

        return BootstrapResult(username, password)
    }

    private fun generatePassword(): String =
        (1..20).map { passwordAlphabet[random.nextInt(passwordAlphabet.length)] }.joinToString("")
}
