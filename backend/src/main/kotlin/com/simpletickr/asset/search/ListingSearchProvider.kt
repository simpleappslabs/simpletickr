package com.simpletickr.asset.search

interface ListingSearchProvider {
    fun search(query: String): List<ListingSearchResult>
}
