package com.simpletickr.asset.search

import com.simpletickr.asset.model.AssetType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class YahooFinanceListingSearchProvider : ListingSearchProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create("https://query2.finance.yahoo.com")

    override fun search(query: String): List<ListingSearchResult> {
        log.debug("Searching Yahoo Finance for '{}'", query)
        return try {
            val candidates = fetchSearchCandidates(query)
            if (candidates.isEmpty()) return emptyList()

            val currencies = fetchCurrencies(candidates.map { it.symbol })
            val results = candidates.map { it.copy(currency = currencies[it.symbol]) }

            log.debug("Returning {} results for '{}'", results.size, query)
            results
        } catch (e: RestClientException) {
            log.warn("Yahoo Finance listing search failed for '{}': {}", query, e.message)
            emptyList()
        }
    }

    private fun fetchSearchCandidates(query: String): List<ListingSearchResult> {
        val response = client.get()
            .uri("/v1/finance/search?q={q}&lang=en-US&quotesCount=10&newsCount=0", query)
            .header("User-Agent", "Mozilla/5.0")
            .retrieve()
            .body(Map::class.java)

        if (response == null) {
            log.warn("Yahoo Finance search returned null response for '{}'", query)
            return emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        val quotes = response["quotes"] as? List<*>
        if (quotes == null) {
            log.warn("No 'quotes' field in Yahoo Finance search response for '{}'; keys: {}", query, response.keys)
            return emptyList()
        }

        log.debug("Yahoo Finance search returned {} raw quotes for '{}'", quotes.size, query)

        return quotes.mapNotNull { item ->
            val q = item as? Map<*, *> ?: return@mapNotNull null
            val symbol = q["symbol"] as? String ?: run {
                log.debug("Skipping quote with no symbol: {}", q)
                return@mapNotNull null
            }
            val name = (q["shortname"] ?: q["longname"]) as? String ?: run {
                log.debug("Skipping {} — no shortname or longname", symbol)
                return@mapNotNull null
            }
            val quoteType = q["quoteType"] as? String ?: "OTHER"
            val exchDisp = q["exchDisp"] as? String
            ListingSearchResult(symbol = symbol, name = name, type = mapQuoteType(quoteType), exchange = exchDisp, currency = null)
        }
    }

    private fun fetchCurrencies(symbols: List<String>): Map<String, String> {
        if (symbols.isEmpty()) return emptyMap()
        log.debug("Fetching currencies for {} symbols via chart endpoint", symbols.size)
        return symbols.mapNotNull { symbol ->
            try {
                val response = client.get()
                    .uri("/v8/finance/chart/{symbol}?interval=1d&range=1d", symbol)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .body(Map::class.java) ?: return@mapNotNull null

                @Suppress("UNCHECKED_CAST")
                val meta = ((response["chart"] as? Map<*, *>)
                    ?.get("result") as? List<*>)
                    ?.firstOrNull() as? Map<*, *>
                    ?: return@mapNotNull null

                val currency = meta["meta"].let { it as? Map<*, *> }?.get("currency") as? String
                    ?: return@mapNotNull null

                symbol to currency
            } catch (e: RestClientException) {
                log.debug("Could not fetch currency for {}: {}", symbol, e.message)
                null
            }
        }.toMap().also { log.debug("Resolved currencies: {}", it) }
    }

    private fun mapQuoteType(quoteType: String) = when (quoteType.uppercase()) {
        "ETF" -> AssetType.ETF
        "EQUITY" -> AssetType.STOCK
        "CRYPTOCURRENCY" -> AssetType.CRYPTO
        else -> AssetType.OTHER
    }
}
