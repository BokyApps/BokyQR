package rocks.myburgh.bokyqr.core

enum class PayloadKind { URL, WIFI, VCARD, EMAIL, SMS, TELEPHONE, GEO, CALENDAR, IMAGE, BINARY, TEXT }
data class ClassifiedPayload(val kind: PayloadKind, val raw: String, val wifi: WifiPayload? = null)

object PayloadClassifier {
    fun classify(raw: String): ClassifiedPayload {
        if (raw.trimStart().startsWith("http://", true) || raw.trimStart().startsWith("https://", true))
            return ClassifiedPayload(PayloadKind.URL, raw)
        WifiParser.parse(raw)?.let { return ClassifiedPayload(PayloadKind.WIFI, raw, it) }
        val s = raw.trimStart()
        val kind = when {
            s.startsWith("BEGIN:VCARD", true) || s.startsWith("MECARD:", true) -> PayloadKind.VCARD
            s.startsWith("BEGIN:VEVENT", true) -> PayloadKind.CALENDAR
            s.startsWith("data:image/", true) && s.contains(";base64,", true) -> PayloadKind.IMAGE
            s.startsWith("mailto:", true) || s.startsWith("MATMSG:", true) -> PayloadKind.EMAIL
            s.startsWith("sms:", true) || s.startsWith("smsto:", true) -> PayloadKind.SMS
            s.startsWith("tel:", true) -> PayloadKind.TELEPHONE
            s.startsWith("geo:", true) -> PayloadKind.GEO
            raw.any { it == '\u0000' || (it.isISOControl() && it !in "\n\r\t") } -> PayloadKind.BINARY
            else -> PayloadKind.TEXT
        }
        return ClassifiedPayload(kind, raw)
    }
}
