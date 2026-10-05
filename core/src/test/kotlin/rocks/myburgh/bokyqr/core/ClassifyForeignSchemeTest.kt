package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refuse list. Every one of these must stay inert text: the app never dials, never joins a
 * Wi-Fi network, never adds a contact, never opens a map, never launches a package and never
 * renders anything as HTML.
 */
class ClassifyForeignSchemeTest {

    private fun displayOnly(raw: String): ScanDecision.DisplayOnly {
        val decision = PayloadPolicy.classify(raw)
        assertTrue("expected DisplayOnly for <$raw> but was $decision", decision is ScanDecision.DisplayOnly)
        return decision as ScanDecision.DisplayOnly
    }

    @Test
    fun `intent urls are display only`() {
        assertEquals(
            DisplayReason.FOREIGN_SCHEME,
            displayOnly("intent://scan/#Intent;scheme=https;package=com.android.chrome;end").reason,
        )
    }

    @Test
    fun `javascript is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("javascript:alert(1)").reason)
    }

    @Test
    fun `file is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("file:///sdcard/secret").reason)
    }

    @Test
    fun `content is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("content://com.android.contacts/contacts").reason)
    }

    @Test
    fun `data is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("data:text/html,abc").reason)
    }

    @Test
    fun `android-app is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("android-app://com.example").reason)
    }

    @Test
    fun `market is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("market://details?id=com.android.vending").reason)
    }

    @Test
    fun `wifi is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("WIFI:T:WPA;S:cafe;P:secret;;").reason)
    }

    @Test
    fun `tel is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("tel:+15551212").reason)
    }

    @Test
    fun `sms, mailto and geo are display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("smsto:15551212:hi").reason)
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("mailto:a@example.com").reason)
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("geo:48.2,16.3?q=here").reason)
    }

    @Test
    fun `vcard is display only`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:Eve\nTEL:+15551212\nEND:VCARD"
        val decision = displayOnly(vcard)
        assertEquals(DisplayReason.CONTROL_CHARACTERS, decision.reason)
        assertEquals(vcard, decision.text)
    }

    @Test
    fun `a non http scheme is never openable even when it looks like a url`() {
        listOf(
            "ftp://example.com/",
            "ws://example.com/",
            "smb://example.com/share",
            "ssh://example.com/",
            "custom-scheme://example.com/thing",
        ).forEach { assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly(it).reason) }
    }

    @Test
    fun `display only keeps the exact scanned text`() {
        val raw = "WIFI:T:WPA;S:cafe;P:secret;;"
        assertEquals(raw, displayOnly(raw).text)
    }
}
