package com.simpletickr.price.provider

import com.simpletickr.price.model.PricePoint
import java.time.LocalDate

interface PriceProvider {
    val name: String
    fun fetchHistory(externalId: String, from: LocalDate, to: LocalDate): List<PricePoint>
}
