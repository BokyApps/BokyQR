package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadClassifierTest {
    @Test fun classifiesAllPayloadKinds() {
        fun kind(raw: String) = PayloadClassifier.classify(raw).kind
        assertEquals(PayloadKind.URL, kind("https://example.com"))
        assertEquals(PayloadKind.URL, kind("http://example.com"))
        assertEquals(PayloadKind.WIFI, kind("WIFI:S:a;T:nopass;;"))
        assertEquals(PayloadKind.VCARD, kind("BEGIN:VCARD\nEND:VCARD"))
        assertEquals(PayloadKind.VCARD, kind("MECARD:N:a;;"))
        assertEquals(PayloadKind.EMAIL, kind("mailto:a@example.com"))
        assertEquals(PayloadKind.EMAIL, kind("MATMSG:TO:a@example.com;"))
        assertEquals(PayloadKind.SMS, kind("sms:123"))
        assertEquals(PayloadKind.SMS, kind("SMSTO:123:hello"))
        assertEquals(PayloadKind.TELEPHONE, kind("tel:123"))
        assertEquals(PayloadKind.GEO, kind("geo:1,2"))
        assertEquals(PayloadKind.CALENDAR, kind("BEGIN:VEVENT\nEND:VEVENT"))
        assertEquals(PayloadKind.IMAGE, kind("data:image/png;base64,aGVsbG8="))
        assertEquals(PayloadKind.BINARY, kind("a\u0000b"))
        assertEquals(PayloadKind.TEXT, kind("hello"))
    }
}
