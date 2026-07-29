package com.simpletickr.auth.model

enum class MembershipRole { OWNER, ADMIN, MEMBER }

data class Membership(
    val id: Long,
    val userId: Long,
    val organizationId: Long,
    val role: MembershipRole,
)
