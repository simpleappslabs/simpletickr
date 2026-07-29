package com.simpletickr.auth.persistence

import com.simpletickr.auth.model.Organization
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class OrganizationRepository(private val jdbcTemplate: JdbcTemplate) {

    fun save(name: String): Organization {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement("INSERT INTO organizations (name) VALUES (?)", arrayOf("id"))
                .apply { setString(1, name) }
        }, keyHolder)
        return Organization(id = keyHolder.key!!.toLong(), name = name)
    }
}
