package rocks.myburgh.bokyqr.core

import org.junit.Assert.*
import org.junit.Test

class WifiParserTest {
    @Test fun parsesSecurityAndOrder() {
        assertEquals("WPA", WifiParser.parse("WIFI:S:home;T:WPA;P:secret;;")?.security)
        assertEquals("WEP", WifiParser.parse("WIFI:P:key;S:home;T:WEP;;")?.security)
        assertEquals("nopass", WifiParser.parse("WIFI:S:open;;")?.security)
        assertEquals("nopass", WifiParser.parse("WIFI:T:;S:open;;")?.security)
        assertTrue(WifiParser.parse("wifi:H:true;T:SAE;P:p;S:s;;")!!.hidden)
        assertFalse(WifiParser.parse("WIFI:S:s;H:false;T:WPA;P:p;;")!!.hidden)
    }

    @Test fun unescapesValues() {
        val wifi = WifiParser.parse("WIFI:S:a\\;b\\:c\\\\d\\,e;T:WPA;P:x\\;y\\:z\\\\q\\,r;;")!!
        assertEquals("a;b:c\\d,e", wifi.ssid)
        assertEquals("x;y:z\\q,r", wifi.password)
    }

    @Test fun rejectsMalformed() {
        listOf("WIFI:S:x", "WIFI:T:WPA;P:p;;", "WIFI:S:x;H:perhaps;;", "WIFI:S:x;T:WPA;;", "WIFI:S:x\\q;;").forEach {
            assertNull(it, WifiParser.parse(it))
        }
    }
}
