package com.simpletickr.brokerimport

sealed class BrokerParseResult {
    data class Recognized(val row: BrokerTransactionRow) : BrokerParseResult()
    data class Skipped(val lineNumber: Int, val reason: String) : BrokerParseResult()
}
