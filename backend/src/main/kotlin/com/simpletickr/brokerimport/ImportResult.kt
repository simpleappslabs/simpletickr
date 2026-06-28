package com.simpletickr.brokerimport

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val rows: List<ImportRowResult>,
)
