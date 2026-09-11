package com.simpletickr.auth.usecase

data class OidcClaims(
    val providerId: String,
    val subject: String,
    val preferredUsername: String?,
    val email: String?,
)
