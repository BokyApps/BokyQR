package rocks.myburgh.bokyqr.core

object BarcodeFormats {
    fun displayName(name: String): String = when (name) {
        "QR_CODE" -> "QR code"
        "EAN_13" -> "EAN-13"
        "EAN_8" -> "EAN-8"
        "UPC_A" -> "UPC-A"
        "UPC_E" -> "UPC-E"
        "CODE_128" -> "Code 128"
        "CODE_39" -> "Code 39"
        "CODE_93" -> "Code 93"
        "ITF" -> "ITF"
        "CODABAR" -> "Codabar"
        "DATA_MATRIX" -> "Data Matrix"
        "PDF_417" -> "PDF417"
        "AZTEC" -> "Aztec"
        else -> name
    }
    fun isQr(name: String): Boolean = name == "QR_CODE"
}
