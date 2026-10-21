package rocks.myburgh.bokyqr.core

import java.net.URLEncoder

/** Builds a browser-safe search URL without treating the query as a destination. */
object WebSearch {
    fun url(query: String): String {
        val normalizedQuery = query.replace(Regex("\\s+"), " ").trim().take(500)
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(normalizedQuery, "UTF-8")
    }
}
