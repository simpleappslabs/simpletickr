package com.simpletickr.asset

import com.simpletickr.shared.CurrencyCode
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class ListingRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Listing> { rs, _ ->
        Listing(
            id = rs.getLong("id"),
            assetId = rs.getLong("asset_id"),
            exchange = rs.getString("exchange"),
            ticker = rs.getString("ticker"),
            currency = CurrencyCode(rs.getString("currency")),
        )
    }

    fun findByAssetId(assetId: Long): List<Listing> =
        jdbcTemplate.query(
            "SELECT id, asset_id, exchange, ticker, currency FROM listings WHERE asset_id = ? ORDER BY id",
            rowMapper, assetId
        )

    fun findById(id: Long): Listing? = try {
        jdbcTemplate.queryForObject(
            "SELECT id, asset_id, exchange, ticker, currency FROM listings WHERE id = ?",
            rowMapper, id
        )
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(assetId: Long, exchange: String?, ticker: String, currency: CurrencyCode): Listing {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO listings (asset_id, exchange, ticker, currency) VALUES (?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setLong(1, assetId)
                setString(2, exchange)
                setString(3, ticker)
                setString(4, currency.value)
            }
        }, keyHolder)
        return Listing(keyHolder.key!!.toLong(), assetId, exchange, ticker, currency)
    }

    fun update(id: Long, exchange: String?, ticker: String, currency: CurrencyCode): Listing? {
        val rows = jdbcTemplate.update(
            "UPDATE listings SET exchange = ?, ticker = ?, currency = ? WHERE id = ?",
            exchange, ticker, currency.value, id
        )
        return if (rows == 0) null else findById(id)
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM listings WHERE id = ?", id)
    }

    fun findDistinctCurrencies(): List<CurrencyCode> =
        jdbcTemplate.queryForList("SELECT DISTINCT currency FROM listings ORDER BY currency", String::class.java)
            .map { CurrencyCode(it) }
}
