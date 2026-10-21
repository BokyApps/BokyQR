package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertTrue
import org.junit.Test

class HexDumpTest {
    @Test fun formatsHexAsciiAndTruncation() {
        val dump = HexDump.format("He\u0000llo".toByteArray(Charsets.ISO_8859_1))
        assertTrue(dump.startsWith("0000  48 65 00 6c 6c 6f"))
        assertTrue(dump.endsWith("He.llo"))
        val truncated = HexDump.format(ByteArray(20), 16)
        assertTrue(truncated.contains("… (truncated, 20 bytes total)"))
    }
}
