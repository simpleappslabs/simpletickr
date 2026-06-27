package com.simpletickr.fx.provider

import com.simpletickr.fx.model.FxRate
import com.simpletickr.shared.CurrencyCode
import java.time.LocalDate

interface FxRateProvider {
    val name: String
    fun fetchHistory(baseCurrency: CurrencyCode, quoteCurrency: CurrencyCode, from: LocalDate, to: LocalDate): List<FxRate>
}
