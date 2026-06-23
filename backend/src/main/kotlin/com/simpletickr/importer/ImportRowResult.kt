package com.simpletickr.importer

enum class ImportStatus { IMPORTED, SKIPPED }

data class ImportRowResult(
    val line: Int,
    val status: ImportStatus,
    val reason: String,
)
