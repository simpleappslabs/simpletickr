package com.simpletickr.asset

import com.simpletickr.asset.search.ListingSearchResult
import com.simpletickr.asset.search.ListingSearchService
import com.simpletickr.generated.api.ListingSearchApi
import com.simpletickr.generated.model.AssetType as GeneratedAssetType
import com.simpletickr.generated.model.ListingSearchResult as ListingSearchResultModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class ListingSearchController(
    private val listingSearchService: ListingSearchService,
) : ListingSearchApi {

    override fun searchListings(q: String): ResponseEntity<List<ListingSearchResultModel>> =
        ResponseEntity.ok(listingSearchService.search(q).map { it.toModel() })

    private fun ListingSearchResult.toModel() = ListingSearchResultModel(
        symbol = symbol,
        name = name,
        type = GeneratedAssetType.valueOf(type.name),
        exchange = exchange,
        currency = currency ?: "",
    )
}
