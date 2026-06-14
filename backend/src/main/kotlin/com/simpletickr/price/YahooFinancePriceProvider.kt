package com.simpletickr.price

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Component
class YahooFinancePriceProvider : PriceProvider {

    override val name = "YAHOO"

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create("https://query1.finance.yahoo.com")

    override fun fetchLatest(externalId: String): PricePoint? =
        fetchHistory(externalId, LocalDate.now().minusDays(7), LocalDate.now()).lastOrNull()

    override fun fetchHistory(externalId: String, from: LocalDate, to: LocalDate): List<PricePoint> {
        val fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val toEpoch = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        return try {
            val response = client.get()
                .uri("/v8/finance/chart/{symbol}?interval=1d&period1={from}&period2={to}",
                    externalId, fromEpoch, toEpoch)
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
                    val price = (close as? Number)?.toDouble() ?: return@mapNotNull null
                    val date = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate()
                    PricePoint(date, BigDecimal.valueOf(price))
                }
                .distinctBy { it.date }
                .sortedBy { it.date }
        } catch (e: RestClientException) {
            log.warn("Failed to fetch prices for {} from Yahoo Finance: {}", externalId, e.message)
            emptyList()
        }
    }
}
