package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VirusTotalUrlIdTest {

    @Test
    fun `example from the VirusTotal docs`() {
        assertEquals(
            "aHR0cDovL3d3dy5zb21lZG9tYWluLmNvbS90aGlzL2lzL215L3VybA",
            virusTotalUrlId("http://www.somedomain.com/this/is/my/url"),
        )
    }

    @Test
    fun `id is unpadded`() {
        val id = virusTotalUrlId("http://www.somedomain.com/this/is/my/url")
        assertNotEquals(true, id.contains("="))
        assertNotEquals(true, id.contains("+"))
        assertNotEquals(true, id.contains("/"))
    }

    @Test
    fun `id is url safe for a byte sequence that would need standard base64 escaping`() {
        // "https://example.com/?a=~?&b=^" style payloads can produce + and / in standard base64.
        val id = virusTotalUrlId("https://example.com/?u=ÿþý")
        assertEquals(-1, id.indexOf('+'))
        assertEquals(-1, id.indexOf('/'))
        assertEquals(-1, id.indexOf('='))
    }

    @Test
    fun `id is computed over the raw string with no canonicalisation`() {
        val upper = virusTotalUrlId("https://EXAMPLE.com/Path")
        val lower = virusTotalUrlId("https://example.com/Path")
        assertNotEquals(upper, lower)
    }

    @Test
    fun `different urls get different ids`() {
        assertNotEquals(
            virusTotalUrlId("https://example.com/a"),
            virusTotalUrlId("https://example.com/b"),
        )
    }
}
