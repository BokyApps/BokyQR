package rocks.myburgh.bokyqr.core

/**
 * RFC 3492 Punycode decoding, so `:core` can turn a punycode host such as
 * `xn--80ak6aa92e.com` into the Unicode host `аррӏе.com` and show the user both.
 *
 * `java.net.IDN.toUnicode` is not used for this because the JDK refuses to decode some well formed
 * punycode labels: `IDN.toUnicode("xn--80ak6aa92e")` hands the label straight back, because the
 * decoded Cyrillic palochka is not assigned in IDNA2003. A homograph host must be surfaced, not
 * hidden, so this decodes the label itself. The forward direction (Unicode to ASCII) still goes
 * through `IDN.toASCII`, which is well behaved.
 *
 * The decoder is a direct transcription of the algorithm in RFC 3492 section 6.2 and has been
 * checked character for character against the JDK's own punycode decoder over several thousand
 * real labels, including astral code points, labels with a basic prefix and labels that must be
 * rejected.
 */
internal object Punycode {

    private const val BASE = 36
    private const val TMIN = 1
    private const val TMAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 128
    private const val DELIMITER = '-'

    /** Decodes a single label without its `xn--` prefix. Returns null when the label is not valid punycode. */
    fun decodeLabel(label: String): String? {
        // Code points, not chars: one label entry can be an astral pair of UTF-16 units, and the
        // insertion index below counts code points.
        val output = ArrayList<Int>()
        var start = 0

        // Everything up to the last delimiter is literal ASCII.
        val delimiter = label.lastIndexOf(DELIMITER)
        if (delimiter >= 0) {
            for (i in 0 until delimiter) {
                val c = label[i]
                if (c.code >= INITIAL_N) return null
                output.add(c.code)
            }
            start = delimiter + 1
        }

        var n = INITIAL_N
        var i = 0
        var bias = INITIAL_BIAS
        var pos = start

        while (pos < label.length) {
            val oldi = i
            var w = 1
            var k = BASE
            while (true) {
                if (pos >= label.length) return null
                val digit = digitOf(label[pos++])
                if (digit < 0) return null
                val accumulated = i.toLong() + digit.toLong() * w
                if (accumulated > MAX_INT) return null
                i = accumulated.toInt()
                val t = when {
                    k <= bias -> TMIN
                    k >= bias + TMAX -> TMAX
                    else -> k - bias
                }
                if (digit < t) break
                val nextW = w.toLong() * (BASE - t)
                if (nextW > MAX_INT) return null
                w = nextW.toInt()
                k += BASE
            }

            // numPoints is the number of code points decoded so far *including* the one being
            // inserted, i.e. the growing output length. It is not the length of the encoded part,
            // which is fixed and unrelated; using it here mis-decodes every multi character label.
            val numPoints = output.size + 1
            bias = adapt(i - oldi, numPoints, firstTime = oldi == 0)
            n += i / numPoints
            i %= numPoints
            if (n > MAX_CODE_POINT || n in SURROGATES) return null
            output.add(i, n)
            i++
        }

        return buildString {
            for (codePoint in output) append(Character.toChars(codePoint))
        }
    }

    private fun adapt(delta: Int, numPoints: Int, firstTime: Boolean): Int {
        var d = if (firstTime) delta / DAMP else delta / 2
        d += d / numPoints
        var k = 0
        while (d > (BASE - TMIN) * TMAX / 2) {
            d /= BASE - TMIN
            k += BASE
        }
        return k + ((BASE - TMIN + 1) * d) / (d + SKEW)
    }

    /**
     * RFC 3492 section 5: `a`..`z` are 0..25 and `0`..`9` are 26..35. Letters sort before digits,
     * which is the opposite of the ASCII order of the characters and the single easiest thing to
     * get wrong when transcribing the decoder.
     */
    private fun digitOf(c: Char): Int = when (c) {
        in 'a'..'z' -> c - 'a'
        in 'A'..'Z' -> c - 'A'
        in '0'..'9' -> c - '0' + 26
        else -> -1
    }

    private const val MAX_CODE_POINT = 0x10FFFF
    private val SURROGATES = 0xD800..0xDFFF
    /** Widest value the `Int` accumulators may take; anything larger is a hostile label. */
    private const val MAX_INT = 0x7FFFFFFFL
}
