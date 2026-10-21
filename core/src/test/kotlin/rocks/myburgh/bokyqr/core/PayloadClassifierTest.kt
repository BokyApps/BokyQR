package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadClassifierTest {
    @Test fun classifiesPayloadKinds() {
        assertEquals(PayloadKind.URL, PayloadClassifier.classify("https://example.com").kind)
        assertEquals(PayloadKind.TEXT, PayloadClassifier.classify("hello").kind)
        assertEquals(PayloadKind.WIFI, PayloadClassifier.classify("WIFI:S:a;T:nopass;;").kind)
        assertEquals(PayloadKind.VCARD, PayloadClassifier.classify("BEGIN:VCARD\nEND:VCARD").kind)
        assertEquals(PayloadKind.EMAIL, PayloadClassifier.classify("mailto:a@example.com").kind)
        assertEquals(PayloadKind.SMS, PayloadClassifier.classify("sms:123").kind)
        assertEquals(PayloadKind.TELEPHONE, PayloadClassifier.classify("tel:123").kind)
        assertEquals(PayloadKind.GEO, PayloadClassifier.classify("geo:1,2").kind)
        assertEquals(PayloadKind.CALENDAR, PayloadClassifier.classify("BEGIN:VEVENT\nEND:VEVENT").kind)
        assertEquals(PayloadKind.IMAGE, PayloadClassifier.classify("data:image/png;base64,aGVsbG8=").kind)
        assertEquals(PayloadKind.BINARY, PayloadClassifier.classify("a\u0000b").kind)
    }
}
