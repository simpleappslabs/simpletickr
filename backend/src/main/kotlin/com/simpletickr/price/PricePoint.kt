package com.simpletickr.price

import java.math.BigDecimal
import java.time.LocalDate

data class PricePoint(val date: LocalDate, val price: BigDecimal)
