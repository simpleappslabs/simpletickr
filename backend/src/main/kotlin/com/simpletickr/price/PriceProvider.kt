package com.simpletickr.price

import java.time.LocalDate

interface PriceProvider {
    val name: String
    fun fetchLatest(externalId: String): PricePoint?
    fun fetchHistory(externalId: String, from: LocalDate, to: LocalDate): List<PricePoint>
}
