package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {
    @Test fun refusedAddressIsEncodedAsSearchQuery() {
        val url = WebSearch.url("http://192.168.1.1/")
        assertEquals("https://duckduckgo.com/?q=http%3A%2F%2F192.168.1.1%2F", url)
        val decision = PayloadPolicy.classify(url)
        assertTrue(decision is ScanDecision.OpenableUrl)
        assertEquals("duckduckgo.com", (decision as ScanDecision.OpenableUrl).hostAscii)
    }
}
