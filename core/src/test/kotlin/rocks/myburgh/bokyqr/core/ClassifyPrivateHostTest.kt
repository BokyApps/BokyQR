package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule under test: a code that points at the device itself, or at the network it sits on, is
 * never offered as an openable URL. It is inert text with a copy button, and the reason says why.
 */
class ClassifyPrivateHostTest {

    private fun displayOnlyReason(raw: String): DisplayReason {
        val decision = PayloadPolicy.classify(raw)
        assertTrue("expected DisplayOnly for <$raw> but was $decision", decision is ScanDecision.DisplayOnly)
        return (decision as ScanDecision.DisplayOnly).reason
    }

    private fun assertLocal(raw: String) =
        assertEquals(
            "expected LOCAL_OR_PRIVATE_HOST for <$raw>",
            DisplayReason.LOCAL_OR_PRIVATE_HOST,
            displayOnlyReason(raw),
        )

    private fun assertOpenable(raw: String) {
        val decision = PayloadPolicy.classify(raw)
        assertTrue("expected OpenableUrl for <$raw> but was $decision", decision is ScanDecision.OpenableUrl)
    }

    @Test
    fun `localhost is never openable`() {
        assertLocal("http://localhost/")
        assertLocal("https://localhost:8080/admin")
        assertLocal("http://LOCALHOST/")
    }

    @Test
    fun `a subdomain of localhost is never openable`() {
        assertLocal("http://api.localhost/")
    }

    @Test
    fun `a single label host is an intranet name and is never openable`() {
        assertLocal("http://nas/")
        assertLocal("http://router.local/")
    }

    @Test
    fun `ipv4 loopback and private ranges are never openable`() {
        assertLocal("http://127.0.0.1:8080/")
        assertLocal("http://127.1.2.3/")
        assertLocal("http://10.0.0.5/")
        assertLocal("http://172.16.4.4/")
        assertLocal("http://172.31.255.255/")
        assertLocal("http://192.168.1.1/")
    }

    @Test
    fun `the cloud metadata endpoint is never openable`() {
        assertLocal("http://169.254.169.254/latest/meta-data/")
        assertLocal("http://169.254.169.254/latest/meta-data/iam/security-credentials/")
        assertLocal("https://169.254.169.254/")
    }

    @Test
    fun `cgnat link local and unspecified ranges are never openable`() {
        assertLocal("http://100.64.0.1/") // RFC 6598 carrier NAT
        assertLocal("http://100.127.255.255/")
        assertLocal("http://0.0.0.0/")
        assertLocal("http://192.0.0.1/")
        assertLocal("http://224.0.0.1/") // multicast
        assertLocal("http://255.255.255.255/")
    }

    @Test
    fun `ipv6 loopback unique local and link local are never openable`() {
        assertLocal("http://[::1]:8080/")
        assertLocal("http://[::]/")
        assertLocal("http://[fd00::1]/")
        assertLocal("http://[fe80::1]/")
        assertLocal("http://[fe80::a00:27ff:fe4e:66a1]/")
    }

    @Test
    fun `an ipv4 mapped ipv6 address is judged by the address it carries`() {
        assertLocal("http://[::ffff:127.0.0.1]/")
        assertLocal("http://[::ffff:169.254.169.254]/")
    }

    @Test
    fun `public addresses stay openable`() {
        assertOpenable("https://example.com/")
        assertOpenable("http://172.32.0.1/") // just outside 172.16.0.0/12
        assertOpenable("http://172.15.255.255/")
        assertOpenable("http://100.63.255.255/")
        assertOpenable("http://100.128.0.1/")
        assertOpenable("http://169.253.0.1/")
        assertOpenable("http://169.255.0.1/")
        assertOpenable("http://11.0.0.1/")
        assertOpenable("http://126.0.0.1/")
        assertOpenable("http://192.167.1.1/")
        assertOpenable("http://[2001:db8::1]/") // documentation range, globally shaped
    }

    @Test
    fun `the raw payload is preserved so it can still be copied`() {
        val raw = "http://192.168.1.1/admin"
        val decision = PayloadPolicy.classify(raw) as ScanDecision.DisplayOnly
        assertEquals(raw, decision.text)
    }
}