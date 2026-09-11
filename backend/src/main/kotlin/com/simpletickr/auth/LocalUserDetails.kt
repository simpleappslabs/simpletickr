package com.simpletickr.auth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Only used during authenticate() (DaoAuthenticationProvider checking a password) — never
 * placed into the SecurityContext for the life of the session. See Principal for that.
 */
class LocalUserDetails(
    val id: Long,
    private val username: String,
    private val passwordHash: String,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()
    override fun getPassword(): String = passwordHash
    override fun getUsername(): String = username
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}
