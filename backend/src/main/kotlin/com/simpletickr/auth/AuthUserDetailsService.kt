package com.simpletickr.auth

import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.user.persistence.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AuthUserDetailsService(
    private val userRepository: UserRepository,
    private val identityRepository: IdentityRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("No such user")
        val identity = identityRepository.findByUserIdAndProviderType(user.id, ProviderType.LOCAL)
            ?: throw UsernameNotFoundException("No local identity for this user")
        return CurrentUser(id = user.id, username = user.username, passwordHash = identity.passwordHash!!)
    }
}
