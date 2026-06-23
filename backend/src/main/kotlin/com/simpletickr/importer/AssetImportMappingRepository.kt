package com.simpletickr.importer

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class AssetImportMappingRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<AssetImportMapping> { rs, _ ->
        AssetImportMapping(
            id = rs.getLong("id"),
            broker = rs.getString("broker"),
            externalName = rs.getString("external_name"),
            assetId = rs.getLong("asset_id"),
        )
    }

    fun findAll(broker: String? = null): List<AssetImportMapping> =
        if (broker != null)
            jdbcTemplate.query(
                "SELECT id, broker, external_name, asset_id FROM asset_import_mappings WHERE broker = ? ORDER BY id",
                rowMapper, broker
            )
        else
            jdbcTemplate.query(
                "SELECT id, broker, external_name, asset_id FROM asset_import_mappings ORDER BY id",
                rowMapper
            )

    fun findByBrokerAndName(broker: String, externalName: String): AssetImportMapping? = try {
        jdbcTemplate.queryForObject(
            "SELECT id, broker, external_name, asset_id FROM asset_import_mappings WHERE broker = ? AND external_name = ?",
            rowMapper, broker, externalName
        )
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(broker: String, externalName: String, assetId: Long): AssetImportMapping {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO asset_import_mappings (broker, external_name, asset_id) VALUES (?, ?, ?)",
                arrayOf("id")
            ).apply {
                setString(1, broker)
                setString(2, externalName)
                setLong(3, assetId)
            }
        }, keyHolder)
        return AssetImportMapping(keyHolder.key!!.toLong(), broker, externalName, assetId)
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM asset_import_mappings WHERE id = ?", id)
    }
}
