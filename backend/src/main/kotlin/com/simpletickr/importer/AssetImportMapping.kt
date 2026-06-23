package com.simpletickr.importer

data class AssetImportMapping(
    val id: Long,
    val broker: String,
    val externalName: String,
    val assetId: Long,
)
