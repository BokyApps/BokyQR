package rocks.myburgh.bokyqr.core

object HexDump {
    fun format(bytes: ByteArray, max: Int = 512): String {
        val count = minOf(bytes.size, max.coerceAtLeast(0))
        val lines = (0 until count step 16).map { start ->
            val end = minOf(start + 16, count)
            val hex = (start until end).joinToString(" ") { "%02x".format(bytes[it].toInt() and 0xff) }
            val ascii = (start until end).joinToString("") {
                val value = bytes[it].toInt() and 0xff
                if (value in 32..126) value.toChar().toString() else "."
            }
            "%04x  %-47s  %s".format(start, hex, ascii)
        }.toMutableList()
        if (bytes.size > count) lines += "… (truncated, ${bytes.size} bytes total)"
        return lines.joinToString("\n")
    }
}
