package com.simpletickr.asset

import org.springframework.stereotype.Service

@Service
class ListingSearchService(private val provider: ListingSearchProvider) {
    fun search(query: String): List<ListingSearchResult> = provider.search(query)
}
