package rocks.myburgh.bokyqr.core

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * The VirusTotal url lookup id for [url]: unpadded URL-safe base64 of the raw URL string.
 *
 * Deliberately no canonicalisation. VirusTotal computes its own id from the exact string it is
 * given, so the client must send back the same bytes the user scanned. Lowercasing, dropping a
 * default port or re-encoding the query would silently produce an id that never matches.
 */
fun virusTotalUrlId(url: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(StandardCharsets.UTF_8))

/**
 * Whether a reputation result forces a second confirmation before "Open in browser".
 *
 * Anything above zero malicious or suspicious verdicts from a provider the user tapped means the
 * URL is not opened on the first tap. A scan never opens anything by itself, and a clean result
 * never opens anything either; the user always taps, and sometimes taps twice.
 */
fun confirmBeforeOpen(malicious: Int, suspicious: Int): Boolean = malicious > 0 || suspicious > 0

/**
 * Whether the "Import QR from gallery" action may run.
 *
 * `false` when the setting is off. The app then hides the gallery button and ignores any shared
 * image, rather than quietly scanning something the user opted out of.
 */
fun galleryImportAllowed(settingEnabled: Boolean): Boolean = settingEnabled
