package com.simpletickr.auth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

/**
 * The generated controller interfaces (openapi-generator, interfaceOnly) have fixed method
 * signatures with no room for an injected `@AuthenticationPrincipal` parameter, so controllers
 * read the principal directly off the (thread-bound) SecurityContext instead.
 */
fun currentUser(): CurrentUser =
    SecurityContextHolder.getContext().authentication.principal as CurrentUser

class CurrentUser(
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
