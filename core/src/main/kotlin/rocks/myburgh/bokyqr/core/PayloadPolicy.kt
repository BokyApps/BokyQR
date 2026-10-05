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
        if (isLocalOrPrivateHost(hostAscii)) {
            // A QR code that points at localhost, at a private range, at a link-local address or
            // at a cloud metadata endpoint is aimed at the device or the network it sits on, not
            // at a website. Opening it can hit a router admin page, a printer, a local dev server
            // or the cloud metadata service, and a provider lookup would happily report "harmless".
            // Refused as DisplayOnly: the text (and its copy button) is still there, so the user can
            // do whatever they actually meant by hand.
            return ScanDecision.DisplayOnly(raw, DisplayReason.LOCAL_OR_PRIVATE_HOST)
        }
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
     * Whether [hostAscii] names something on this device or on the network it sits on, rather
     * than a public site.
     *
     * Covers `localhost` and `*.localhost`, single-label intranet names (`http://nas/`), the IPv4
     * loopback / private / CGNAT / link-local / IETF-reserved ranges including the cloud metadata
     * address 169.254.169.254, and the IPv6 equivalents (`::1`, `fc00::/7`, `fe80::/10`, and
     * IPv4-mapped forms of any of the above).
     *
     * Deliberately conservative about ranges that only *sound* private: a globally routable address
     * in, say, 203.0.113.0/24 is not private and is still openable. What is refused is what can
     * only ever be reached from behind the user's own router.
     */
    internal fun isLocalOrPrivateHost(hostAscii: String): Boolean {
        if (hostAscii.startsWith("[")) {
            return isLocalOrPrivateIpv6(hostAscii.trim('[', ']'))
        }
        val octets = ipv4Octets(hostAscii)
        // Not an address literal, so it is a name. `localhost` and everything under it are
        // resolved by the device itself, and a name with no dot in it can only be resolved by the
        // user's own DNS server or mDNS, i.e. on the network they are standing in.
        if (octets == null) {
            return hostAscii == "localhost" ||
                hostAscii.endsWith(".localhost") || // RFC 6762: always the device itself
                hostAscii.endsWith(".local") || // RFC 6762: mDNS, link-local multicast only
                hostAscii.endsWith(".home.arpa") || // RFC 8375: home networks only
                !hostAscii.contains('.') // a single label can only resolve on the local network
        }
        return when {
            octets[0] == 0 -> true // "this network" / unspecified
            octets[0] == 10 -> true // 10.0.0.0/8 private
            octets[0] == 127 -> true // 127.0.0.0/8 loopback
            octets[0] == 100 && octets[1] in 64..127 -> true // 100.64.0.0/10 CGNAT, RFC 6598
            octets[0] == 169 && octets[1] == 254 -> true // link-local, incl. 169.254.169.254
            octets[0] == 172 && octets[1] in 16..31 -> true // 172.16.0.0/12 private
            octets[0] == 192 && octets[1] == 168 -> true // 192.168.0.0/16 private
            octets[0] == 192 && octets[1] == 0 && octets[2] == 0 -> true // 192.0.0.0/24 IETF
            octets[0] >= 224 -> true // multicast, reserved, broadcast
            else -> false
        }
    }

    /** Four decimal octets, or null when [host] is not a dotted-quad IPv4 literal. */
    private fun ipv4Octets(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0 until 4) {
            val part = parts[i]
            // "010" and "0x7f" style octets are not addresses; refuse to guess at them.
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            octets[i] = part.toInt()
            if (octets[i] > 255) return null
        }
        return octets
    }

    private fun isLocalOrPrivateIpv6(host: String): Boolean {
        val literal = host.substringBefore('%').lowercase() // drop any zone index
        if (literal == "::" || literal == "::1") return true
        // IPv4-mapped and IPv4-compatible forms carry an embedded IPv4 address; judge that.
        val embedded = ipv4InIpv6(literal)
        if (embedded != null) return isLocalOrPrivateHost(embedded)
        val head = literal.substringBefore(':').padStart(4, '0')
        val value = head.toIntOrNull(16) ?: return false
        val topByte = value shr 8
        return when {
            // fc00::/7 unique local.
            topByte and 0xFE == 0xFC -> true
            // fe80::/10 link local: 1111111010, i.e. a 0xFE byte followed by an 8 or a 9.
            topByte == 0xFE && (value shr 4 and 0x0F) in 0x8..0x9 -> true
            else -> false
        }
    }

    /** `::ffff:127.0.0.1` style tails, normalised back to a dotted quad. */
    private fun ipv4InIpv6(literal: String): String? {
        val tail = literal.substringAfterLast(':')
        if (!tail.contains('.')) return null
        return ipv4Octets(tail)?.joinToString(".")
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
