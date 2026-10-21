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

    @Test fun multilineQueryProducesOpenableUrl() {
        val url = WebSearch.url("first line\n\tsecond line\r\nthird")
        val decision = PayloadPolicy.classify(url)
        assertTrue(decision is ScanDecision.OpenableUrl)
    }

    @Test fun longQueryIsCappedToProduceOpenableUrl() {
        val url = WebSearch.url("x".repeat(5000))
        val decision = PayloadPolicy.classify(url)
        assertTrue(decision is ScanDecision.OpenableUrl)
    }
}
