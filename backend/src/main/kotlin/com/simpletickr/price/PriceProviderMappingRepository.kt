package com.simpletickr.price

import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class PriceProviderMappingRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<PriceProviderMapping> { rs, _ ->
        PriceProviderMapping(
            id = rs.getLong("id"),
            listingId = rs.getLong("listing_id"),
            provider = rs.getString("provider"),
            externalId = rs.getString("external_id"),
        )
    }

    fun findAll(): List<PriceProviderMapping> =
        jdbcTemplate.query(
            "SELECT id, listing_id, provider, external_id FROM price_provider_mappings",
            rowMapper
        )

    fun findByListingAndProvider(listingId: Long, provider: String): PriceProviderMapping? = try {
        jdbcTemplate.queryForObject(
            "SELECT id, listing_id, provider, external_id FROM price_provider_mappings WHERE listing_id = ? AND provider = ?",
            rowMapper, listingId, provider
        )
    } catch (_: EmptyResultDataAccessException) { null }

    fun upsert(listingId: Long, provider: String, externalId: String): PriceProviderMapping {
        val existing = findByListingAndProvider(listingId, provider)
        return if (existing != null) {
            jdbcTemplate.update(
                "UPDATE price_provider_mappings SET external_id = ? WHERE listing_id = ? AND provider = ?",
                externalId, listingId, provider
            )
            existing.copy(externalId = externalId)
        } else {
            val keyHolder = GeneratedKeyHolder()
            jdbcTemplate.update({ con ->
                con.prepareStatement(
                    "INSERT INTO price_provider_mappings (listing_id, provider, external_id) VALUES (?, ?, ?)",
                    arrayOf("id")
                ).apply {
                    setLong(1, listingId)
                    setString(2, provider)
                    setString(3, externalId)
                }
            }, keyHolder)
            PriceProviderMapping(keyHolder.key!!.toLong(), listingId, provider, externalId)
        }
    }

    fun delete(listingId: Long, provider: String): Boolean =
        jdbcTemplate.update(
            "DELETE FROM price_provider_mappings WHERE listing_id = ? AND provider = ?",
            listingId, provider
        ) > 0
}
