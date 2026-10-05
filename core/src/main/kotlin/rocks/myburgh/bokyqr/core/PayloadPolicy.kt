package rocks.myburgh.bokyqr.core

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException

/**
 * The whole payload policy. Pure JVM, no Android, no network, no disk.
 *
 * The rule this object exists to enforce: a scanned QR is *data*, never an instruction. The
 * only payloads that may ever be launched are an http/https URL that passes every check below,
 * and a FIDO URI. Everything else is text on a screen with a copy button.
 */
object PayloadPolicy {

    /** Longest URL the app will offer to open. */
    const val MAX_URL_LENGTH: Int = 2048

    /**
     * A passkey provisioning QR is exactly the scheme, a slash, and 20 to 2048 decimal digits.
     * Anything else, including trailing digits, slashes, queries or fragments, is inert text.
     */
    private val PASSKEY = Regex("^FIDO:/[0-9]{20,2048}$", RegexOption.IGNORE_CASE)

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

    /** `%0d`, `%0a`, `%00` and friends: control characters smuggled through percent-encoding. */
    private val PERCENT_ENCODED_CONTROL = Regex("%(?:0[0-9A-Fa-f]|1[0-9A-Fa-f]|7[Ff])")

    /** A syntactically valid DNS name once ASCII labels are required. */
    private val ASCII_HOST = Regex(
        "^(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)*" +
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$",
    )

    fun classify(raw: String): ScanDecision {
        val trimmed = raw.trim()

        if (trimmed.isEmpty()) return ScanDecision.DisplayOnly(raw, DisplayReason.BLANK)

        // Passkey first: the pattern already excludes whitespace and control characters.
        normalizePasskey(trimmed)?.let { return ScanDecision.Passkey(it) }

        if (trimmed.length > MAX_URL_LENGTH) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.TOO_LONG)
        }
        // Checked on the payload itself, before anything tries to parse it.
        if (trimmed.any { it.isISOControl() }) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.CONTROL_CHARACTERS)
        }
        if (trimmed.any { it.isWhitespace() }) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.WHITESPACE_INSIDE)
        }
        if (trimmed.contains('\\')) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.BACKSLASH_TRAIN)
        }
        if (PERCENT_ENCODED_CONTROL.containsMatchIn(trimmed)) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.CONTROL_CHARACTERS)
        }

        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.MALFORMED)
        }

        val scheme = uri.scheme?.lowercase()
            ?: return ScanDecision.DisplayOnly(raw, DisplayReason.MALFORMED)

        // The single most important line in the file: only http and https may ever be launched.
        if (scheme != "http" && scheme != "https") {
            return ScanDecision.DisplayOnly(raw, DisplayReason.FOREIGN_SCHEME)
        }
        if (uri.isOpaque) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.MALFORMED)
        }
        if (uri.rawUserInfo != null) {
            return ScanDecision.DisplayOnly(raw, DisplayReason.USERINFO)
        }

        val host = uri.host
            ?: return ScanDecision.DisplayOnly(raw, DisplayReason.UNSAFE_HOST)

        val hostAscii = asciiHost(host)
            ?: return ScanDecision.DisplayOnly(raw, DisplayReason.UNSAFE_HOST)
        val hostUnicode = unicodeHost(hostAscii)

        return ScanDecision.OpenableUrl(url = trimmed, hostUnicode = hostUnicode, hostAscii = hostAscii)
    }

    /**
     * Returns the normalised `FIDO:/<digits>` URI, or null when this is not a passkey QR.
     * Outer whitespace is trimmed and the scheme is uppercased, nothing else is touched.
     */
    fun normalizePasskey(trimmed: String): String? {
        if (!PASSKEY.matches(trimmed)) return null
        return "FIDO:/" + trimmed.substring("FIDO:/".length)
    }

    /** Punycode / ASCII form of [host], or null when the host is not usable. */
    private fun asciiHost(host: String): String? {
        if (host.startsWith("[")) {
            // IPv6 literal, already bracketed by the URI parser.
            return if (host.endsWith("]") && host.length > 2) host else null
        }
        if (host.length > 253) return null
        val ascii = try {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase()
        } catch (_: IllegalArgumentException) {
            return null
        }
        return if (ASCII_HOST.matches(ascii)) ascii else null
    }

    /**
     * Unicode form of a punycode host, or the ASCII host itself when a label does not decode.
     * When the two differ the UI shows both, because that is exactly the pair a reader needs in
     * order to spot `xn--80ak6aa92e.com` masquerading as something else.
     */
    private fun unicodeHost(hostAscii: String): String {
        if (!hostAscii.contains("xn--", ignoreCase = true)) return hostAscii
        val decoded = hostAscii.split('.').joinToString(".") { label ->
            if (label.startsWith("xn--", ignoreCase = true)) {
                Punycode.decodeLabel(label.substring(4)) ?: label
            } else {
                label
            }
        }
        return decoded
    }
}
