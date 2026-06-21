package com.simpletickr.asset

import com.simpletickr.shared.CurrencyCode
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class AssetRepository(private val jdbcTemplate: JdbcTemplate) {

    private data class AssetRow(
        val assetId: Long, val isin: String?, val name: String, val type: AssetType,
        val listingId: Long, val exchange: String?, val ticker: String, val currency: CurrencyCode,
    )

    private data class AssetRowWithPrice(
        val assetId: Long, val isin: String?, val name: String, val type: AssetType,
        val listingId: Long, val exchange: String?, val ticker: String, val currency: CurrencyCode,
        val lastPriceDate: LocalDate?, val lastPrice: BigDecimal?,
    )

    private fun aggregateRows(rows: List<AssetRow>): List<Asset> =
        rows.groupBy { it.assetId }.map { (assetId, assetRows) ->
            val first = assetRows.first()
            Asset(
                id = assetId,
                isin = first.isin,
                name = first.name,
                type = first.type,
                listings = assetRows.map { r -> Listing(r.listingId, assetId, r.exchange, r.ticker, r.currency) },
            )
        }

    private fun aggregateRowsWithPrice(rows: List<AssetRowWithPrice>): List<AssetWithPrices> =
        rows.groupBy { it.assetId }.map { (assetId, assetRows) ->
            val first = assetRows.first()
            AssetWithPrices(
                id = assetId,
                isin = first.isin,
                name = first.name,
                type = first.type,
                listings = assetRows.map { r ->
                    ListingWithPrice(r.listingId, assetId, r.exchange, r.ticker, r.currency, r.lastPriceDate, r.lastPrice)
                },
            )
        }

    private fun rowMapper(rs: java.sql.ResultSet): AssetRow = AssetRow(
        assetId = rs.getLong("asset_id"),
        isin = rs.getString("isin"),
        name = rs.getString("name"),
        type = AssetType.valueOf(rs.getString("type")),
        listingId = rs.getLong("listing_id"),
        exchange = rs.getString("exchange"),
        ticker = rs.getString("ticker"),
        currency = CurrencyCode(rs.getString("currency")),
    )

    private fun rowMapperWithPrice(rs: java.sql.ResultSet): AssetRowWithPrice = AssetRowWithPrice(
        assetId = rs.getLong("asset_id"),
        isin = rs.getString("isin"),
        name = rs.getString("name"),
        type = AssetType.valueOf(rs.getString("type")),
        listingId = rs.getLong("listing_id"),
        exchange = rs.getString("exchange"),
        ticker = rs.getString("ticker"),
        currency = CurrencyCode(rs.getString("currency")),
        lastPriceDate = rs.getObject("last_price_date", LocalDate::class.java),
        lastPrice = rs.getBigDecimal("last_price"),
    )

    fun findAll(): List<Asset> {
        val rows = jdbcTemplate.query("""
            SELECT a.id AS asset_id, a.isin, a.name, a.type,
                   l.id AS listing_id, l.exchange, l.ticker, l.currency
            FROM assets a
            JOIN listings l ON l.asset_id = a.id
            ORDER BY a.id, l.id
        """.trimIndent()) { rs, _ -> rowMapper(rs) }
        return aggregateRows(rows)
    }

    fun findAllWithLatestPrice(): List<AssetWithPrices> {
        val rows = jdbcTemplate.query("""
            SELECT a.id AS asset_id, a.isin, a.name, a.type,
                   l.id AS listing_id, l.exchange, l.ticker, l.currency,
                   aph.date AS last_price_date, aph.close_price AS last_price
            FROM assets a
            JOIN listings l ON l.asset_id = a.id
            LEFT JOIN LATERAL (
                SELECT date, close_price
                FROM asset_price_history
                WHERE listing_id = l.id
                ORDER BY date DESC
                LIMIT 1
            ) aph ON true
            ORDER BY a.id, l.id
        """.trimIndent()) { rs, _ -> rowMapperWithPrice(rs) }
        return aggregateRowsWithPrice(rows)
    }

    fun findById(id: Long): Asset? {
        val rows = jdbcTemplate.query("""
            SELECT a.id AS asset_id, a.isin, a.name, a.type,
                   l.id AS listing_id, l.exchange, l.ticker, l.currency
            FROM assets a
            JOIN listings l ON l.asset_id = a.id
            WHERE a.id = ?
            ORDER BY l.id
        """.trimIndent(), { rs, _ -> rowMapper(rs) }, id)
        return aggregateRows(rows).firstOrNull()
    }

    fun save(isin: String?, name: String, type: AssetType): Asset {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO assets (isin, name, type) VALUES (?, ?, ?)",
                arrayOf("id")
            ).apply {
                setString(1, isin)
                setString(2, name)
                setString(3, type.name)
            }
        }, keyHolder)
        return Asset(keyHolder.key!!.toLong(), isin, name, type)
    }

    fun update(id: Long, isin: String?, name: String, type: AssetType): Asset? {
        val rows = jdbcTemplate.update(
            "UPDATE assets SET isin = ?, name = ?, type = ? WHERE id = ?",
            isin, name, type.name, id
        )
        return if (rows == 0) null else findById(id)
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM assets WHERE id = ?", id)
    }
}
