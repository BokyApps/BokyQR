package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifyPasskeyTest {

    private val digits40 = "1".repeat(40)

    private fun passkey(raw: String): ScanDecision.Passkey {
        val decision = PayloadPolicy.classify(raw)
        assertTrue("expected Passkey for <$raw> but was $decision", decision is ScanDecision.Passkey)
        return decision as ScanDecision.Passkey
    }

    private fun displayOnly(raw: String): ScanDecision.DisplayOnly {
        val decision = PayloadPolicy.classify(raw)
        assertTrue("expected DisplayOnly for <$raw> but was $decision", decision is ScanDecision.DisplayOnly)
        return decision as ScanDecision.DisplayOnly
    }

    @Test
    fun `forty digits is a passkey and the uri is rebuilt from the digits`() {
        assertEquals("FIDO:/$digits40", passkey("FIDO:/$digits40").fidoUri)
    }

    @Test
    fun `lowercase scheme is normalised to uppercase FIDO`() {
        assertEquals("FIDO:/$digits40", passkey("fido:/$digits40").fidoUri)
        assertEquals("FIDO:/$digits40", passkey("FiDo:/$digits40").fidoUri)
    }

    @Test
    fun `outer whitespace is trimmed before matching`() {
        assertEquals("FIDO:/$digits40", passkey("  FIDO:/$digits40\r\n").fidoUri)
    }

    @Test
    fun `the minimum and maximum digit counts are accepted`() {
        assertEquals("FIDO:/" + "1".repeat(20), passkey("FIDO:/" + "1".repeat(20)).fidoUri)
        val max = "1".repeat(2048)
        assertEquals("FIDO:/$max", passkey("FIDO:/$max").fidoUri)
    }

    @Test
    fun `too few digits is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:/12").reason)
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:/" + "1".repeat(19)).reason)
    }

    @Test
    fun `too many digits is display only`() {
        displayOnly("FIDO:/" + "1".repeat(2049))
    }

    @Test
    fun `a trailing path segment is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:/$digits40/extra").reason)
    }

    @Test
    fun `a query or fragment is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:/$digits40?x=1").reason)
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:/$digits40#f").reason)
    }

    @Test
    fun `non digits are display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:/" + "a".repeat(40)).reason)
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:/${digits40}x").reason)
    }

    @Test
    fun `a missing slash is display only`() {
        assertEquals(DisplayReason.FOREIGN_SCHEME, displayOnly("FIDO:$digits40").reason)
    }

    @Test
    fun `a passkey uri is never openable as a url`() {
        assertTrue(PayloadPolicy.classify("FIDO:/$digits40") !is ScanDecision.OpenableUrl)
    }
}
