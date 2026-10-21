package rocks.myburgh.bokyqr.core

import java.net.URLEncoder

/** Builds a browser-safe search URL without treating the query as a destination. */
object WebSearch {
    fun url(query: String): String = "https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8")
}
