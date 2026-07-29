package com.simpletickr.auth.persistence

import com.simpletickr.auth.model.Membership
import com.simpletickr.auth.model.MembershipRole
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class MembershipRepository(private val jdbcTemplate: JdbcTemplate) {

    fun save(userId: Long, organizationId: Long, role: MembershipRole): Membership {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO memberships (user_id, organization_id, role) VALUES (?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, userId)
                setLong(2, organizationId)
                setString(3, role.name)
            }
        }, keyHolder)
        return Membership(id = keyHolder.key!!.toLong(), userId = userId, organizationId = organizationId, role = role)
    }
}
