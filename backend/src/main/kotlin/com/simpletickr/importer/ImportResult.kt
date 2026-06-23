package com.simpletickr.importer

data class ImportResult(
    val imported: Int,
    val skipped: Int,
    val rows: List<ImportRowResult>,
)
