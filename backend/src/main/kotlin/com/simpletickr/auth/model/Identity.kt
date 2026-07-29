package com.simpletickr.auth.model

enum class ProviderType { LOCAL, OIDC }

data class Identity(
    val id: Long,
    val userId: Long,
    val providerType: ProviderType,
    val providerId: String,
    val subject: String?,
    val passwordHash: String?,
) {
    companion object {
        const val LOCAL_PROVIDER_ID = "local"
    }
}
