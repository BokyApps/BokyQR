package rocks.myburgh.bokyqr.core

/**
 * The only thing the app is ever allowed to do with a scanned payload is act on one of these
 * three outcomes. Anything that is not an [OpenableUrl] or a [Passkey] is [DisplayOnly]:
 * shown as inert text with a copy button, never launched, never dialled, never connected to.
 */
sealed interface ScanDecision {

    /**
     * An http/https URL that survived every policy check in [PayloadPolicy] and may therefore
     * be offered to the user as "Open in browser" (a user tap, never automatic).
     *
     * @param url the payload exactly as scanned, only outer whitespace trimmed. It is never
     *   canonicalised, because VirusTotal ids are computed over the raw string.
     * @param hostUnicode the internationalised host, for example `правда.com`.
     * @param hostAscii the ASCII/punycode host, for example `xn--80ak6aa92e.com`.
     */
    data class OpenableUrl(
        val url: String,
        val hostUnicode: String,
        val hostAscii: String,
    ) : ScanDecision

    /**
     * A FIDO / passkey provisioning QR. The app never speaks CTAP, BLE or the hybrid tunnel;
     * it hands the URI to whatever already handles the FIDO scheme on the device.
     */
    data class Passkey(val fidoUri: String) : ScanDecision

    /**
     * Inert text. [text] is the raw payload and [reason] explains why nothing was launched.
     */
    data class DisplayOnly(
        val text: String,
        val reason: DisplayReason,
    ) : ScanDecision
}

/** Why a payload was refused every launch action. Purely descriptive; the UI maps it to text. */
enum class DisplayReason {
    /** Nothing but whitespace. */
    BLANK,

    /** Carries CR, LF, NUL or another ISO control character, raw or percent-encoded. */
    CONTROL_CHARACTERS,

    /** Contains whitespace inside the payload. */
    WHITESPACE_INSIDE,

    /** Contains a backslash, the classic "trust the left half of this" URL confusion trick. */
    BACKSLASH_TRAIN,

    /** Longer than the policy limit. */
    TOO_LONG,

    /** `intent:`, `javascript:`, `file:`, `data:`, `content:`, `android-app:`, `market:`, `tel:`, `geo:`, `mailto:`, `sms:`, `WIFI:`, `vCard`, plain text, ... */
    FOREIGN_SCHEME,

    /** Carries `user:password@`, which is used to confuse a human reading the URL. */
    USERINFO,

    /** No parseable host, or a host that is not a plausible DNS name / IP literal. */
    UNSAFE_HOST,

    /**
     * The host names this device or its own network: `localhost`, a single-label intranet name,
     * an RFC 1918 / CGNAT / link-local IPv4 address (including the cloud metadata endpoint
     * 169.254.169.254), or the IPv6 equivalents. Opened only by hand, never by the app.
     */
    LOCAL_OR_PRIVATE_HOST,

    /** Does not parse as a URI at all. */
    MALFORMED,
}
