package com.simpletickr.portfolio

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class HoldingRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findByPortfolioId(portfolioId: Long): List<Holding> =
        jdbcTemplate.query("""
            SELECT
                a.id                                                              AS asset_id,
                a.name,
                MIN(l.ticker)                                                     AS ticker,
                SUM(CASE WHEN t.type = 'BUY' THEN t.quantity ELSE -t.quantity END) AS quantity,
                SUM(CASE WHEN t.type = 'BUY' THEN t.quantity * t.price ELSE 0 END) /
                    NULLIF(SUM(CASE WHEN t.type = 'BUY' THEN t.quantity ELSE 0 END), 0) AS avg_cost_basis,
                (
                    SELECT ph.close_price
                    FROM asset_price_history ph
                    JOIN listings pl ON pl.id = ph.listing_id
                    WHERE pl.asset_id = a.id
                    ORDER BY ph.date DESC, pl.id ASC
                    LIMIT 1
                ) AS current_price
            FROM transactions t
            JOIN listings l ON l.id = t.listing_id
            JOIN assets a ON a.id = l.asset_id
            WHERE t.portfolio_id = ?
            GROUP BY a.id, a.name
            HAVING SUM(CASE WHEN t.type = 'BUY' THEN t.quantity ELSE -t.quantity END) > 0
        """.trimIndent(), { rs, _ ->
            val quantity = rs.getBigDecimal("quantity")
            val avgCostBasis = rs.getBigDecimal("avg_cost_basis")
            val currentPrice = rs.getBigDecimal("current_price")
            Holding(
                assetId = rs.getLong("asset_id"),
                ticker = rs.getString("ticker"),
                name = rs.getString("name"),
                quantity = quantity,
                avgCostBasis = avgCostBasis,
                totalCost = quantity.multiply(avgCostBasis),
                unrealizedGain = currentPrice?.let { it.subtract(avgCostBasis).multiply(quantity) },
            )
        }, portfolioId)
}
