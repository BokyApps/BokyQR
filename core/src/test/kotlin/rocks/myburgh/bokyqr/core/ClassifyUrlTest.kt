package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifyUrlTest {

    private fun openable(raw: String): ScanDecision.OpenableUrl {
        val decision = PayloadPolicy.classify(raw)
        assertTrue("expected OpenableUrl for <$raw> but was $decision", decision is ScanDecision.OpenableUrl)
        return decision as ScanDecision.OpenableUrl
    }

    private fun displayOnly(raw: String): ScanDecision.DisplayOnly {
        val decision = PayloadPolicy.classify(raw)
        assertTrue("expected DisplayOnly for <$raw> but was $decision", decision is ScanDecision.DisplayOnly)
        return decision as ScanDecision.DisplayOnly
    }

    @Test
    fun `plain https url is openable and the url is unchanged`() {
        val decision = openable("https://example.com/a?b=1&c=2")
        assertEquals("https://example.com/a?b=1&c=2", decision.url)
        assertEquals("example.com", decision.hostAscii)
        assertEquals("example.com", decision.hostUnicode)
    }

    @Test
    fun `plain http url is openable`() {
        assertEquals("http://www.somedomain.com/this/is/my/url", openable("http://www.somedomain.com/this/is/my/url").url)
    }

    @Test
    fun `idn host exposes both unicode and ascii forms and they differ`() {
        val decision = openable("https://xn--80ak6aa92e.com/")
        assertEquals("xn--80ak6aa92e.com", decision.hostAscii)
        assertNotEquals(decision.hostAscii, decision.hostUnicode)
        assertEquals("аррӏе.com", decision.hostUnicode)
    }

    @Test
    fun `uppercase scheme is openable and the payload is never rewritten`() {
        // The URL is handed on exactly as scanned: VirusTotal ids are computed over these bytes.
        assertEquals("HTTPS://example.com/", openable("HTTPS://example.com/").url)
    }

    @Test
    fun `outer whitespace is trimmed and the payload stays openable`() {
        assertEquals("https://example.com/", openable("  https://example.com/\n").url)
    }

    @Test
    fun `host must parse`() {
        assertTrue(displayOnly("http://exa_mple.com/").reason == DisplayReason.UNSAFE_HOST)
        assertTrue(displayOnly("http:///path").reason == DisplayReason.UNSAFE_HOST)
        assertTrue(displayOnly("http://").reason == DisplayReason.MALFORMED)
        assertTrue(displayOnly("https://-bad-.example/").reason == DisplayReason.UNSAFE_HOST)
        assertTrue(displayOnly("https://example..com/").reason == DisplayReason.UNSAFE_HOST)
    }

    @Test
    fun `userinfo is display only`() {
        val decision = displayOnly("https://user:pass@example.com/")
        assertEquals(DisplayReason.USERINFO, decision.reason)
    }

    @Test
    fun `userinfo without a password is display only`() {
        assertEquals(DisplayReason.USERINFO, displayOnly("https://user@example.com/").reason)
    }

    @Test
    fun `percent encoded control characters are display only`() {
        assertEquals(DisplayReason.CONTROL_CHARACTERS, displayOnly("http://evil.example/%0d%0a").reason)
        assertEquals(DisplayReason.CONTROL_CHARACTERS, displayOnly("http://evil.example/%00").reason)
        assertEquals(DisplayReason.CONTROL_CHARACTERS, displayOnly("https://example.com/%1b%5b0m").reason)
    }

    @Test
    fun `raw control characters are rejected before parsing`() {
        assertEquals(DisplayReason.CONTROL_CHARACTERS, displayOnly("https://example.com/\r\nX-Injected: 1").reason)
        assertEquals(DisplayReason.CONTROL_CHARACTERS, displayOnly("https://example.com/\u0000").reason)
        assertEquals(DisplayReason.CONTROL_CHARACTERS, displayOnly("https://exam\tple.com/").reason)
    }

    @Test
    fun `whitespace inside is display only`() {
        assertEquals(DisplayReason.WHITESPACE_INSIDE, displayOnly("https://exa mple.com/").reason)
    }

    @Test
    fun `backslash tricks are display only`() {
        assertEquals(DisplayReason.BACKSLASH_TRAIN, displayOnly("https://example.com\\@evil.com/").reason)
        assertEquals(DisplayReason.BACKSLASH_TRAIN, displayOnly("https://example.com\\evil.com/").reason)
    }

    @Test
    fun `urls longer than the limit are display only`() {
        val long = "https://example.com/" + "a".repeat(PayloadPolicy.MAX_URL_LENGTH)
        assertEquals(DisplayReason.TOO_LONG, displayOnly(long).reason)
    }

    @Test
    fun `a url exactly at the limit is still openable`() {
        val prefix = "https://example.com/"
        val atLimit = prefix + "a".repeat(PayloadPolicy.MAX_URL_LENGTH - prefix.length)
        assertEquals(PayloadPolicy.MAX_URL_LENGTH, atLimit.length)
        openable(atLimit)
    }

    @Test
    fun `a globally routable ipv6 literal host is openable`() {
        assertEquals("[2001:db8::1]", openable("http://[2001:db8::1]:8080/x").hostAscii)
    }

    @Test
    fun `an ipv6 loopback literal is display only, not openable`() {
        // ::1 names this device. See ClassifyPrivateHostTest for the whole private-range rule.
        assertEquals(
            DisplayReason.LOCAL_OR_PRIVATE_HOST,
            displayOnly("http://[::1]:8080/x").reason,
        )
    }

    @Test
    fun `a payload of only spaces is display only`() {
        assertEquals(DisplayReason.BLANK, displayOnly("   ").reason)
        assertEquals(DisplayReason.BLANK, displayOnly("").reason)
        assertEquals(DisplayReason.BLANK, displayOnly("\t\n ").reason)
    }

    @Test
    fun `plain text is display only`() {
        assertEquals(DisplayReason.MALFORMED, displayOnly("abc").reason)
        assertEquals(DisplayReason.WHITESPACE_INSIDE, displayOnly("hello world").reason)
        assertEquals(DisplayReason.BLANK, displayOnly("   ").reason)
    }
}
