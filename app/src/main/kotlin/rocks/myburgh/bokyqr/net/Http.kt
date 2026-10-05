package rocks.myburgh.bokyqr.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The only place in the app that talks to the network, and it only ever talks to the three
 * reputation providers the user explicitly tapped. It never fetches the scanned URL itself:
 * the URL is sent as data in a request body or path, never as the page that gets loaded.
 *
 * No request body, header or response is ever logged, because those carry the user's key and the
 * URL the user scanned.
 *
 * A response body is read through a hard 1 MiB cap. A hostile or broken endpoint cannot make
 * this app buffer an unbounded amount of memory, and an over-long body is refused outright
 * rather than truncated into a half-valid JSON document.
 */
internal class HttpResult(val code: Int, val body: String)

internal object Http {

    private const val TIMEOUT_MILLIS = 20_000

    /** Hard ceiling on a response body. 1 MiB is far above any real provider response. */
    const val MAX_BODY_BYTES: Int = 1_048_576

    /**
     * A GET with the default 20-second connect/read timeout. Providers that poll in a tight loop
     * can pass a smaller [timeoutMillis] so one stuck socket cannot eat the whole wait budget; the
     * body cap is unchanged.
     */
    suspend fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMillis: Int = TIMEOUT_MILLIS,
    ): HttpResult = request("GET", url, headers, body = null, contentType = null, timeoutMillis)

    suspend fun postForm(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
    ): HttpResult {
        val encoded = fields.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        return request(
            "POST", url, headers, encoded, "application/x-www-form-urlencoded; charset=utf-8",
        )
    }

    suspend fun postJson(url: String, headers: Map<String, String>, json: String): HttpResult =
        request("POST", url, headers, json, "application/json; charset=utf-8")

    private suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        contentType: String?,
        timeoutMillis: Int = TIMEOUT_MILLIS,
    ): HttpResult = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = false
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            contentType?.let { setRequestProperty("Content-Type", it) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResult(code, readCapped(stream))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Reads [stream] as UTF-8, refusing to buffer more than [MAX_BODY_BYTES].
     *
     * Reads at most one byte past the cap: the extra byte is the proof that the body really is
     * over-long, and it is the reason to stop instead of quietly returning a truncated payload.
     * Throws [IOException] on an over-long or unreadable body. The stream is always closed and
     * the bytes are never logged.
     */
    private fun readCapped(stream: InputStream?): String {
        if (stream == null) return ""
        val buffer = ByteArray(MAX_BODY_BYTES)
        var total = 0
        stream.use { input ->
            while (total < MAX_BODY_BYTES) {
                val read = input.read(buffer, total, MAX_BODY_BYTES - total)
                if (read < 0) break
                total += read
            }
            if (total == MAX_BODY_BYTES && input.read() != -1) {
                throw IOException(
                    "Response body exceeds the ${MAX_BODY_BYTES}-byte limit and was refused.",
                )
            }
        }
        return String(buffer, 0, total, StandardCharsets.UTF_8)
    }
}
