package com.simpletickr.shared

@JvmInline
value class CurrencyCode(val value: String) {
    init {
        require(value.matches(Regex("[A-Z]{3}"))) { "Invalid currency code: $value" }
    }

    override fun toString() = value
}
