package com.simpletickr.auth.persistence

import com.simpletickr.auth.model.Identity
import com.simpletickr.auth.model.ProviderType
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class IdentityRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Identity> { rs, _ ->
        Identity(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            providerType = ProviderType.valueOf(rs.getString("provider_type")),
            providerId = rs.getString("provider_id"),
            subject = rs.getString("subject"),
            passwordHash = rs.getString("password_hash"),
        )
    }

    fun count(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identities", Long::class.java)!!

    fun findByUserIdAndProviderType(userId: Long, providerType: ProviderType): Identity? = try {
        jdbcTemplate.queryForObject(
            "SELECT id, user_id, provider_type, provider_id, subject, password_hash FROM identities WHERE user_id = ? AND provider_type = ?",
            rowMapper, userId, providerType.name,
        )
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(identity: Identity): Identity {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO identities (user_id, provider_type, provider_id, subject, password_hash) VALUES (?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, identity.userId)
                setString(2, identity.providerType.name)
                setString(3, identity.providerId)
                setString(4, identity.subject)
                setString(5, identity.passwordHash)
            }
        }, keyHolder)
        return identity.copy(id = keyHolder.key!!.toLong())
    }

    fun updatePasswordHash(id: Long, passwordHash: String) {
        jdbcTemplate.update("UPDATE identities SET password_hash = ?, updated_at = NOW() WHERE id = ?", passwordHash, id)
    }
}
