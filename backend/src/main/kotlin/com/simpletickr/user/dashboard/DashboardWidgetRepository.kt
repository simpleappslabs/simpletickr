package com.simpletickr.user.dashboard

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class DashboardWidgetRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {

    private val rowMapper = { rs: java.sql.ResultSet, _: Int ->
        RawDashboardWidget(
            id = rs.getLong("id"),
            type = DashboardWidgetType.valueOf(rs.getString("type")),
            configJson = rs.getString("config"),
        )
    }

    fun findAll(): List<RawDashboardWidget> =
        jdbcTemplate.query(
            "SELECT id, type, config FROM dashboard_widget ORDER BY created_at",
            rowMapper
        )

    fun insert(type: DashboardWidgetType, config: WidgetConfig): RawDashboardWidget {
        val keyHolder = GeneratedKeyHolder()
        val configJson = objectMapper.writeValueAsString(config)
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO dashboard_widget (type, config) VALUES (?, ?::jsonb)",
                arrayOf("id")
            ).apply {
                setString(1, type.name)
                setString(2, configJson)
            }
        }, keyHolder)
        return RawDashboardWidget(keyHolder.key!!.toLong(), type, configJson)
    }

    fun updateConfig(id: Long, config: WidgetConfig): Boolean {
        val configJson = objectMapper.writeValueAsString(config)
        return jdbcTemplate.update(
            "UPDATE dashboard_widget SET config = ?::jsonb WHERE id = ?",
            configJson, id
        ) > 0
    }

    fun delete(id: Long): Boolean =
        jdbcTemplate.update("DELETE FROM dashboard_widget WHERE id = ?", id) > 0

    fun findRawById(id: Long): RawDashboardWidget? =
        jdbcTemplate.query(
            "SELECT id, type, config FROM dashboard_widget WHERE id = ?",
            rowMapper,
            id
        ).firstOrNull()
}

data class RawDashboardWidget(
    val id: Long,
    val type: DashboardWidgetType,
    val configJson: String,
)
