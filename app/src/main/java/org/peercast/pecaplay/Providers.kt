package org.peercast.pecaplay

import android.content.Context
import android.content.SearchRecentSuggestionsProvider
import android.provider.SearchRecentSuggestions

private const val AUTHORITY = "org.peercast.pecaplay.SuggestionProvider"

class SuggestionProvider : SearchRecentSuggestionsProvider() {
    init {
        setupSuggestions(AUTHORITY, SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES)
    }
}

fun saveRecentQuery(c: Context, query: String) {
    if (query.isEmpty())
        return
    val suggestions = SearchRecentSuggestions(
            c, AUTHORITY,
            SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES
    )
    suggestions.saveRecentQuery(query, null)
}
