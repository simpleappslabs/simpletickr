package com.simpletickr.portfolio.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class PortfolioValueHistoryRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findOldestTransactionDate(portfolioId: Long): LocalDate? =
        jdbcTemplate.query(
            "SELECT MIN(date) FROM transactions WHERE portfolio_id = ?",
            { rs, _ -> rs.getDate(1)?.toLocalDate() },
            portfolioId,
        ).firstOrNull()
}
