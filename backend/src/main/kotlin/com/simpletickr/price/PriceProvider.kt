package com.simpletickr.price

import java.time.LocalDate

interface PriceProvider {
    val name: String
    fun fetchHistory(externalId: String, from: LocalDate, to: LocalDate): List<PricePoint>
}
