package rocks.myburgh.bokyqr.core

/** Parsed WiFi Easy Connect payload. No Android types or connection side effects. */
data class WifiPayload(val ssid: String, val security: String, val password: String?, val hidden: Boolean)

object WifiParser {
    fun parse(raw: String): WifiPayload? {
        if (!raw.startsWith("WIFI:", ignoreCase = true) || !raw.endsWith(";;")) return null
        val fields = linkedMapOf<String, String>()
        val body = raw.substring(5, raw.length - 2)
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (c in body) {
            if (escaped) { current.append('\\').append(c); escaped = false }
            else if (c == '\\') escaped = true
            else if (c == ';') { parts += current.toString(); current.setLength(0) }
            else current.append(c)
        }
        if (escaped) return null
        if (current.isNotEmpty()) parts += current.toString()
        for (part in parts) {
            val split = part.indexOf(':')
            if (split <= 0) return null
            val key = part.substring(0, split)
            if (key !in setOf("S", "T", "P", "H")) continue
            if (key in fields) return null
            val value = unescape(part.substring(split + 1)) ?: return null
            fields[key] = value
        }
        val ssid = fields["S"]?.takeIf { it.isNotEmpty() } ?: return null
        val security = fields["T"]?.takeIf { it.isNotEmpty() } ?: "nopass"
        val hidden = fields["H"]?.let { when (it.lowercase()) { "true" -> true; "false" -> false; else -> return null } } ?: false
        val password = fields["P"]?.takeIf { it.isNotEmpty() }
        if (security.equals("nopass", true) && password != null) return null
        if (!security.equals("nopass", true) && password == null) return null
        return WifiPayload(ssid, security, password, hidden)
    }

    private fun unescape(value: String): String? {
        val out = StringBuilder(); var i = 0
        while (i < value.length) {
            if (value[i] == '\\') {
                if (++i >= value.length || value[i] !in "\\;,:\"") return null
            }
            out.append(value[i++])
        }
        val text = out.toString()
        return if (text.length >= 2 && text.first() == '"' && text.last() == '"') text.substring(1, text.length - 1) else text
    }
}
