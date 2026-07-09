package com.simpletickr.account.persistence

import com.simpletickr.account.model.Account
import com.simpletickr.account.model.AccountType
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class AccountRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper<Account> { rs, _ ->
        Account(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            broker = rs.getString("broker"),
            accountType = AccountType.valueOf(rs.getString("account_type")),
            currency = rs.getString("currency"),
            accountNumber = rs.getString("account_number"),
            institution = rs.getString("institution"),
            transactionCount = rs.getLong("transaction_count"),
        )
    }

    private val baseSelect = """
        SELECT a.id, a.name, a.broker, a.account_type, a.currency, a.account_number, a.institution,
               COUNT(t.id) AS transaction_count
        FROM accounts a
        LEFT JOIN transactions t ON t.account_id = a.id
    """.trimIndent()

    fun findAll(): List<Account> =
        jdbcTemplate.query("$baseSelect GROUP BY a.id ORDER BY a.name ASC", rowMapper)

    fun findById(id: Long): Account? = try {
        jdbcTemplate.queryForObject("$baseSelect WHERE a.id = ? GROUP BY a.id", rowMapper, id)
    } catch (_: EmptyResultDataAccessException) { null }

    fun save(account: Account): Account {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ con ->
            con.prepareStatement(
                "INSERT INTO accounts (name, broker, account_type, currency, account_number, institution) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf("id")
            ).apply {
                setString(1, account.name)
                setString(2, account.broker)
                setString(3, account.accountType.name)
                setString(4, account.currency)
                setString(5, account.accountNumber)
                setString(6, account.institution)
            }
        }, keyHolder)
        return account.copy(id = keyHolder.key!!.toLong())
    }

    fun update(account: Account): Account? {
        val updated = jdbcTemplate.update(
            "UPDATE accounts SET name = ?, broker = ?, account_type = ?, currency = ?, account_number = ?, institution = ? WHERE id = ?",
            account.name, account.broker, account.accountType.name, account.currency,
            account.accountNumber, account.institution, account.id,
        )
        return if (updated == 0) null else account
    }

    fun delete(id: Long) {
        jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", id)
    }
}
