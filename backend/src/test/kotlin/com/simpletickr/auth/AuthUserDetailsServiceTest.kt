package com.simpletickr.auth

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import com.simpletickr.auth.persistence.IdentityRepository
import com.simpletickr.user.model.User
import com.simpletickr.user.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.UsernameNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthUserDetailsServiceTest {

    private val userRepository = mock<UserRepository>()
    private val identityRepository = mock<IdentityRepository>()
    private val service = AuthUserDetailsService(userRepository, identityRepository)

    @Test
    fun `loadUserByUsername throws when no such user`() {
        whenever(userRepository.findByUsername("nobody")).thenReturn(null)

        assertFailsWith<UsernameNotFoundException> { service.loadUserByUsername("nobody") }
    }

    @Test
    fun `loadUserByUsername throws when user has no local identity`() {
        whenever(userRepository.findByUsername("admin")).thenReturn(User(1L, "admin"))
        whenever(identityRepository.findByUserIdAndProviderType(1L, ProviderType.LOCAL)).thenReturn(null)

        assertFailsWith<UsernameNotFoundException> { service.loadUserByUsername("admin") }
    }

    @Test
    fun `loadUserByUsername returns a CurrentUser wrapping the id, username and password hash`() {
        whenever(userRepository.findByUsername("admin")).thenReturn(User(1L, "admin"))
        whenever(identityRepository.findByUserIdAndProviderType(1L, ProviderType.LOCAL)).thenReturn(
            Identity(id = 5L, userId = 1L, providerType = ProviderType.LOCAL, providerId = Identity.LOCAL_PROVIDER_ID, subject = null, passwordHash = "hash")
        )

        val result = service.loadUserByUsername("admin") as CurrentUser

        assertEquals(1L, result.id)
        assertEquals("admin", result.username)
        assertEquals("hash", result.password)
    }
}
