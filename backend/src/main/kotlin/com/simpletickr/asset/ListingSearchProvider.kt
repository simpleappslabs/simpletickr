package com.simpletickr.asset

interface ListingSearchProvider {
    fun search(query: String): List<ListingSearchResult>
}
