package com.simpletickr.asset.search

import org.springframework.stereotype.Service

@Service
class ListingSearchService(private val provider: ListingSearchProvider) {
    fun search(query: String): List<ListingSearchResult> = provider.search(query)
}
