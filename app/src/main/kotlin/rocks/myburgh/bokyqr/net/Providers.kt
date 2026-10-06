package rocks.myburgh.bokyqr.net

import kotlinx.coroutines.delay
import org.json.JSONObject
import rocks.myburgh.bokyqr.core.virusTotalUrlId
import java.net.URLEncoder

/** A provider call failed in a way the user needs to know about. The message is safe to show. */
class ProviderException(message: String) : Exception(message)

/**
 * `Http` deliberately does not follow redirects: an API host that answers with a 302 to somewhere
 * else is either broken or being unhelpful, and silently chasing it would send the scanned URL and
 * the user's key to a host this app never vetted. Say so instead of reporting a bare 3xx.
 */
private fun redirected(provider: String, response: HttpResult): String =
    "$provider answered with a redirect (HTTP ${response.code}). This app does not follow redirects, " +
        "so nothing was sent."

/** The outcome of a provider the user explicitly tapped. Nothing here is ever produced on its own. */
sealed interface ProviderOutcome {
    data class VirusTotal(
        val malicious: Int,
        val suspicious: Int,
        val harmless: Int,
        val undetected: Int,
        /** The VirusTotal lookup id for the scanned URL: `virusTotalUrlId(url)`. */
        val urlId: String,
    ) : ProviderOutcome {
        /** The public report page for [urlId], for the "open details" hand-off in the UI. */
        val guiUrl: String get() = "https://www.virustotal.com/gui/url/$urlId"
    }

    data class Urlscan(val uuid: String, val resultUrl: String) : ProviderOutcome

    /** URLhaus is a known-malware lookup, not a full scan. [listed] false means "not listed". */
    data class Urlhaus(val listed: Boolean, val threat: String?) : ProviderOutcome
}

/**
 * VirusTotal, using the user's own key. The app never ships one.
 *
 * Flow, exactly as the public API expects:
 *  1. `GET /api/v3/urls/{id}` where `{id}` is the unpadded URL-safe base64 of the raw scanned URL.
 *  2. On 404, `POST /api/v3/urls` with the URL as a form field to submit it for analysis.
 *  3. Poll `GET /api/v3/analyses/{id}` on a flat 20-second cadence for up to 90 seconds until the
 *     analysis is completed. If it still is not done, the URL has been submitted and the next tap
 *     picks up the saved report.
 *
 * The cadence is chosen to fit the documented limit rather than to feel quick. The public API
 * allows 4 requests per minute and 500 per day, so a fresh URL costs exactly two requests (the
 * lookup and the submit) and then four polls inside the 90-second budget: six requests per 90
 * seconds is 4 per minute, with nothing left over. The old 3-second cadence issued ~30 requests
 * for the same work and earned a 429 every time.
 *
 * A 429 stops everything: the public API is 500 requests per day and 4 per minute, so hammering it
 * only makes things worse. The request is never retried after a 429. A `Retry-After` on a
 * non-429 response is honoured, clamped, and otherwise the fixed cadence applies.
 */
object VirusTotalClient {

    private const val BASE = "https://www.virustotal.com/api/v3"
    private const val RATE_LIMIT =
        "VirusTotal rate limit reached (HTTP 429). The public API allows 4 requests per minute " +
            "and 500 per day. Wait a little and try again."
    private const val BAD_KEY = "VirusTotal rejected the API key. Check it in Settings."

    /**
     * How long a fresh URL's analysis is given before we stop polling. A new link often needs
     * longer than the old ~30-second ladder; the wait is a flat budget, not an exponential climb.
     * At [POLL_INTERVAL_MILLIS] this budget allows exactly four polls.
     */
    private const val POLL_BUDGET_MILLIS = 90_000L

    /**
     * Spacing between analysis polls, and also the wait before the first one. 20 seconds is the
     * slowest cadence that still fits the documented 4-requests-per-minute budget for the whole
     * lookup/submit/poll sequence; see the class comment for the arithmetic.
     */
    private const val POLL_INTERVAL_MILLIS = 20_000L

    /** Bounds on an honoured `Retry-After`, so a hostile or broken header cannot stall the UI. */
    private const val RETRY_AFTER_MIN_MILLIS = 1_000L
    private const val RETRY_AFTER_MAX_MILLIS = 60_000L

    /** One stuck socket must not eat the whole budget: each VirusTotal call is capped at 12s. */
    private const val CALL_TIMEOUT_MILLIS = 12_000

    suspend fun lookup(url: String, apiKey: String): ProviderOutcome.VirusTotal {
        val id = virusTotalUrlId(url)
        val existing = Http.get("$BASE/urls/$id", mapOf("x-apikey" to apiKey))
        when (existing.code) {
            200 -> return existing.body.statsOf("data", id)
            404 -> Unit // never seen before: fall through and submit it.
            429 -> throw ProviderException(RATE_LIMIT)
            401, 403 -> throw ProviderException(BAD_KEY)
            in 300..399 -> throw ProviderException(redirected("VirusTotal", existing))
            else -> throw ProviderException("VirusTotal lookup failed (HTTP ${existing.code}).")
        }

        val submitted = Http.postForm("$BASE/urls", mapOf("x-apikey" to apiKey), mapOf("url" to url))
        when (submitted.code) {
            200, 201 -> Unit
            429 -> throw ProviderException(RATE_LIMIT)
            401, 403 -> throw ProviderException(BAD_KEY)
            in 300..399 -> throw ProviderException(redirected("VirusTotal", submitted))
            else -> throw ProviderException("VirusTotal submission failed (HTTP ${submitted.code}).")
        }
        val analysisId = JSONObject(submitted.body).getJSONObject("data").getString("id")
        return pollAnalysis(analysisId, id, apiKey)
    }

    private suspend fun pollAnalysis(
        analysisId: String,
        urlId: String,
        apiKey: String,
    ): ProviderOutcome.VirusTotal {
        // The id comes from the server. Encode it as one path segment so a crafted value cannot
        // inject a slash, a query or an authority and change the host we talk to.
        val path = encodeSegment(analysisId)
        val startedAt = System.currentTimeMillis()
        var wait = POLL_INTERVAL_MILLIS
        while (true) {
            // Wait first. Submitting and polling in the same instant is what used to trigger the
            // rate limiter, and it gave the user no more information than a spinner does.
            delay(wait)
            val response = Http.get(
                "$BASE/analyses/$path",
                mapOf("x-apikey" to apiKey),
                CALL_TIMEOUT_MILLIS,
            )
            when (response.code) {
                200 -> {
                    val attributes = JSONObject(response.body).getJSONObject("data")
                        .getJSONObject("attributes")
                    if (attributes.optString("status") == "completed") {
                        return attributes.getJSONObject("stats").toOutcome(urlId)
                    }
                }
                429 -> throw ProviderException(RATE_LIMIT)
                401, 403 -> throw ProviderException(BAD_KEY)
                in 300..399 -> throw ProviderException(redirected("VirusTotal", response))
                else -> throw ProviderException("VirusTotal analysis failed (HTTP ${response.code}).")
            }
            if (System.currentTimeMillis() - startedAt >= POLL_BUDGET_MILLIS) break
            // A server that told us when to come back gets to decide how long that is, but not
            // outside a range that would either hammer it again or leave the user waiting forever.
            wait = response.retryAfterMillis
                ?.coerceIn(RETRY_AFTER_MIN_MILLIS, RETRY_AFTER_MAX_MILLIS)
                ?: POLL_INTERVAL_MILLIS
        }
        throw ProviderException(
            "The URL was submitted to VirusTotal, but the analysis did not finish in time. Tap " +
                "Check with VirusTotal again in about a minute; the next tap uses the saved report.",
        )
    }

    /**
     * Encodes [value] so it is exactly one path segment: every reserved character that could
     * re-target the request (notably `/`, `?` and `#`) is percent-escaped, and a space becomes
     * `%20` rather than the query-string `+`. The host in [BASE] is never affected.
     */
    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun String.statsOf(rootKey: String, urlId: String): ProviderOutcome.VirusTotal {
        val stats = JSONObject(this).getJSONObject(rootKey)
            .getJSONObject("attributes")
            .getJSONObject("last_analysis_stats")
        return stats.toOutcome(urlId)
    }

    private fun JSONObject.toOutcome(urlId: String) = ProviderOutcome.VirusTotal(
        malicious = optInt("malicious"),
        suspicious = optInt("suspicious"),
        harmless = optInt("harmless"),
        undetected = optInt("undetected"),
        urlId = urlId,
    )
}

/**
 * urlscan.io, using the user's own key. Scans are always submitted with `visibility: unlisted`,
 * which is hard-coded here; the app can never create a public scan through this client. Unlisted
 * scans are still visible to urlscan Pro researchers, which the UI discloses.
 */
object UrlscanClient {

    private const val SCAN_URL = "https://urlscan.io/api/v1/scan/"

    /**
     * The only shape of `uuid` we are willing to build a URL out of. Anything else is discarded.
     */
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    suspend fun submit(url: String, apiKey: String): ProviderOutcome.Urlscan {
        val payload = JSONObject()
            .put("url", url)
            .put("visibility", "unlisted")
            .toString()
        val response = Http.postJson(SCAN_URL, mapOf("API-Key" to apiKey), payload)
        when (response.code) {
            in 200..299 -> Unit
            429 -> throw ProviderException("urlscan.io rate limit reached. Try again shortly.")
            401, 403 -> throw ProviderException("urlscan.io rejected the API key. Check it in Settings.")
            in 300..399 -> throw ProviderException(redirected("urlscan.io", response))
            else -> throw ProviderException("urlscan.io submission failed (HTTP ${response.code}).")
        }
        val body = JSONObject(response.body)
        val uuid = body.optString("uuid")

        // The server's `result` field is ignored outright. A tampered or misconfigured response
        // could put any URI in it, and it would reach ACTION_VIEW without ever meeting
        // PayloadPolicy. The result URL is rebuilt here from the uuid alone, and only when that
        // uuid is a well-formed UUID, so the string we hand out is always one we constructed
        // ourselves: https://urlscan.io/result/<uuid>/ and nothing else.
        val resultUrl = if (UUID.matches(uuid)) "https://urlscan.io/result/$uuid/" else ""
        return ProviderOutcome.Urlscan(uuid = uuid, resultUrl = resultUrl)
    }
}

/**
 * URLhaus, using the user's own Auth-Key. This is a *known-malware lookup*, not a full scan: it
 * answers only whether this exact URL is already in the URLhaus blocklist. A miss is "not in the
 * URLhaus malware list", which is not the same as "clean". The UI says so.
 */
object UrlhausClient {

    private const val LOOKUP_URL = "https://urlhaus-api.abuse.ch/v1/url/"

    suspend fun lookup(url: String, authKey: String): ProviderOutcome.Urlhaus {
        val response = Http.postForm(LOOKUP_URL, mapOf("Auth-Key" to authKey), mapOf("url" to url))
        when (response.code) {
            in 200..299 -> Unit
            429 -> throw ProviderException("URLhaus rate limit reached. Try again shortly.")
            401, 403 -> throw ProviderException("URLhaus rejected the Auth-Key. Check it in Settings.")
            in 300..399 -> throw ProviderException(redirected("URLhaus", response))
            else -> throw ProviderException("URLhaus lookup failed (HTTP ${response.code}).")
        }
        val body = JSONObject(response.body)
        return when (body.optString("query_status")) {
            "ok" -> ProviderOutcome.Urlhaus(
                listed = true,
                threat = body.optString("threat").ifBlank { null },
            )
            "no_results" -> ProviderOutcome.Urlhaus(listed = false, threat = null)
            "invalid_auth_key" -> throw ProviderException("URLhaus rejected the Auth-Key. Check it in Settings.")
            "illegal_url" -> throw ProviderException("URLhaus rejected this URL as malformed.")
            else -> throw ProviderException("URLhaus returned an unexpected response.")
        }
    }
}
