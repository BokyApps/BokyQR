package rocks.myburgh.bokyqr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeFormatsTest {
    @Test fun displayNames() {
        val names = mapOf("QR_CODE" to "QR code", "EAN_13" to "EAN-13", "EAN_8" to "EAN-8", "UPC_A" to "UPC-A", "UPC_E" to "UPC-E", "CODE_128" to "Code 128", "CODE_39" to "Code 39", "CODE_93" to "Code 93", "ITF" to "ITF", "CODABAR" to "Codabar", "DATA_MATRIX" to "Data Matrix", "PDF_417" to "PDF417", "AZTEC" to "Aztec")
        names.forEach { (name, expected) -> assertEquals(expected, BarcodeFormats.displayName(name)) }
        assertEquals("OTHER", BarcodeFormats.displayName("OTHER"))
        assertTrue(BarcodeFormats.isQr("QR_CODE"))
        assertFalse(BarcodeFormats.isQr("EAN_13"))
    }
}
