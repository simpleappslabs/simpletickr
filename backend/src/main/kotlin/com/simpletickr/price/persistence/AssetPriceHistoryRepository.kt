package com.simpletickr.price.persistence

import com.simpletickr.price.model.PricePoint
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class AssetPriceHistoryRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<PricePoint> { rs, _ ->
        PricePoint(
            date = rs.getDate("date").toLocalDate(),
            price = rs.getBigDecimal("close_price"),
        )
    }

    fun upsert(listingId: Long, points: List<PricePoint>) {
        if (points.isEmpty()) return
        jdbcTemplate.batchUpdate(
            """INSERT INTO asset_price_history (listing_id, date, close_price)
               VALUES (?, ?, ?)
               ON CONFLICT (listing_id, date) DO UPDATE SET close_price = EXCLUDED.close_price""",
            points.map { arrayOf<Any>(listingId, it.date, it.price) }
        )
    }

    fun findLatestByListingId(listingId: Long): PricePoint? =
        jdbcTemplate.query(
            "SELECT date, close_price FROM asset_price_history WHERE listing_id = ? ORDER BY date DESC LIMIT 1",
            rowMapper, listingId
        ).firstOrNull()

    fun findEarliestByListingId(listingId: Long): PricePoint? =
        jdbcTemplate.query(
            "SELECT date, close_price FROM asset_price_history WHERE listing_id = ? ORDER BY date ASC LIMIT 1",
            rowMapper, listingId
        ).firstOrNull()

    fun findByListingId(listingId: Long, from: LocalDate, to: LocalDate): List<PricePoint> =
        jdbcTemplate.query(
            "SELECT date, close_price FROM asset_price_history WHERE listing_id = ? AND date BETWEEN ? AND ? ORDER BY date",
            rowMapper, listingId, from, to
        )

    // The single most recent point strictly before `date` — combined with findByListingId(from, to),
    // this is everything a forward-fill over [from, to] needs: one boundary row to seed day one,
    // plus every point actually inside the window. Deliberately not "from inception to `to`" — a
    // long-lived listing (years of daily prices) would have every short window (e.g. "1M") refetch
    // its entire history for no benefit, since only the day-one boundary and the in-window points
    // ever get used.
    fun findLatestBefore(listingId: Long, date: LocalDate): PricePoint? =
        jdbcTemplate.query(
            "SELECT date, close_price FROM asset_price_history WHERE listing_id = ? AND date < ? ORDER BY date DESC LIMIT 1",
            rowMapper, listingId, date
        ).firstOrNull()
}
