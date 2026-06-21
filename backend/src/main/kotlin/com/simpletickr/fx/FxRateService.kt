package com.simpletickr.fx

import com.simpletickr.shared.CurrencyCode
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class FxRateService(
    private val providers: List<FxRateProvider>,
    private val fxRateRepository: FxRateRepository,
) {

    // Returns the rate for the given date (or most recent before it), fetching from Yahoo if not in DB.
    // The returned FxRate.date may be earlier than the requested date (e.g. weekend/holiday).
    fun lookupOrFetch(baseCurrency: CurrencyCode, quoteCurrency: CurrencyCode, date: LocalDate): FxRate? {
        fxRateRepository.findOnDate(baseCurrency, quoteCurrency, date)?.let { return it }

        val provider = providers.firstOrNull() ?: return null
        val rates = provider.fetchHistory(baseCurrency, quoteCurrency, date.minusDays(7), date)
        if (rates.isEmpty()) return null
        fxRateRepository.upsert(rates)
        return rates.filter { it.date <= date }.maxByOrNull { it.date }
    }
}
