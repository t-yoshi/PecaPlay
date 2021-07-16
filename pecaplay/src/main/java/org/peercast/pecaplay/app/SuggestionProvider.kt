package org.peercast.pecaplay.app

import android.content.Context
import android.content.SearchRecentSuggestionsProvider
import android.provider.SearchRecentSuggestions

private const val AUTHORITY = "org.peercast.pecaplay.app.SuggestionProvider"

class SuggestionProvider : SearchRecentSuggestionsProvider() {
    init {
        setupSuggestions(AUTHORITY, DATABASE_MODE_QUERIES)
    }
}

/**検索履歴の保存*/
fun saveRecentQuery(c: Context, query: String) {
    if (query.isEmpty())
        return
    val suggestions = SearchRecentSuggestions(
        c, AUTHORITY,
        SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES
    )
    suggestions.saveRecentQuery(query, null)
}
