package com.simpletickr.fx.provider

import com.simpletickr.fx.model.FxRate
import com.simpletickr.shared.CurrencyCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Component
class YahooFinanceFxRateProvider : FxRateProvider {

    override val name = "YAHOO"

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create("https://query1.finance.yahoo.com")

    override fun fetchHistory(baseCurrency: CurrencyCode, quoteCurrency: CurrencyCode, from: LocalDate, to: LocalDate): List<FxRate> {
        // Yahoo symbol for currency pairs: "EURUSD=X" → close price = units of quote per 1 base
        val symbol = "$baseCurrency${quoteCurrency}=X"
        val fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val toEpoch = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        return try {
            val response = client.get()
                .uri("/v8/finance/chart/{symbol}?interval=1d&period1={from}&period2={to}",
                    symbol, fromEpoch, toEpoch)
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .body(Map::class.java) ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
            val chart = (response["chart"] as? Map<String, Any>) ?: return emptyList()
            val result = (chart["result"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return emptyList()
            val timestamps = result["timestamp"] as? List<*> ?: return emptyList()
            val indicators = result["indicators"] as? Map<*, *> ?: return emptyList()
            val closes = ((indicators["quote"] as? List<*>)?.firstOrNull() as? Map<*, *>)
                ?.get("close") as? List<*> ?: return emptyList()

            timestamps.zip(closes)
                .mapNotNull { (ts, close) ->
                    val epoch = (ts as? Number)?.toLong() ?: return@mapNotNull null
                    val rate = (close as? Number)?.toDouble() ?: return@mapNotNull null
                    val date = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate()
                    FxRate(baseCurrency, quoteCurrency, date, BigDecimal.valueOf(rate))
                }
                .distinctBy { it.date }
                .sortedBy { it.date }
        } catch (e: RestClientException) {
            log.warn("Failed to fetch FX rates for {} from Yahoo Finance: {}", symbol, e.message)
            emptyList()
        }
    }
}
