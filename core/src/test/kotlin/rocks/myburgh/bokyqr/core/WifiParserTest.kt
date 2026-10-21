package rocks.myburgh.bokyqr.core

import org.junit.Assert.*
import org.junit.Test

class WifiParserTest {
    @Test fun parsesSecurityAndFieldOrders() {
        assertEquals("WPA", WifiParser.parse("WIFI:S:home;T:WPA;P:secret;;")?.security)
        assertEquals("WEP", WifiParser.parse("WIFI:P:key;S:home;T:WEP;;")?.security)
        assertEquals("nopass", WifiParser.parse("WIFI:S:open;T:nopass;;")?.security)
        assertEquals("nopass", WifiParser.parse("WIFI:S:open;;")?.security)
        assertEquals("SAE", WifiParser.parse("wifi:H:true;T:SAE;P:p;S:s;;")?.security)
        assertTrue(WifiParser.parse("WIFI:S:s;H:true;T:WPA;P:p;;")!!.hidden)
        assertFalse(WifiParser.parse("WIFI:S:s;H:false;T:WPA;P:p;;")!!.hidden)
        assertFalse(WifiParser.parse("WIFI:S:s;T:WPA;P:p;;")!!.hidden)
        assertEquals("quoted ssid", WifiParser.parse("WIFI:S:\"quoted ssid\";T:nopass;;")?.ssid)
        assertEquals("quoted password", WifiParser.parse("WIFI:S:s;T:WPA;P:\"quoted password\";;")?.password)
        assertEquals("\"home\"", WifiParser.parse("""WIFI:S:\"home\";T:nopass;;""")?.ssid)
        assertEquals("\"secret\"", WifiParser.parse("""WIFI:S:s;T:WPA;P:\"secret\";;""")?.password)
        val explicitNoPassword = WifiParser.parse("WIFI:S:cafe;T:nopass;P:unused;;")!!
        assertNull(explicitNoPassword.password)
        val omittedSecurity = WifiParser.parse("WIFI:S:cafe;P:unused;;")!!
        assertEquals("nopass", omittedSecurity.security)
        assertNull(omittedSecurity.password)
    }

    @Test fun unescapesValues() {
        val wifi = WifiParser.parse("WIFI:S:a\\;b\\:c\\\\d\\,e\\\"f;T:WPA;P:x\\;y\\:z\\\\q\\,r\\\"s;;")!!
        assertEquals("a;b:c\\d,e\"f", wifi.ssid)
        assertEquals("x;y:z\\q,r\"s", wifi.password)
    }

    @Test fun rejectsMalformed() {
        listOf("WIFI:S:x", "WIFI:T:WPA;P:p;;", "WIFI:S:x;H:perhaps;;", "WIFI:S:x;T:WPA;;", "WIFI:S:x\\q;;", "WIFI:S:x;T:nopass", "WIFI:T:nopass;;", "WIFI:S:x;S:y;T:nopass;;").forEach {
            assertNull(it, WifiParser.parse(it))
        }
    }
}
