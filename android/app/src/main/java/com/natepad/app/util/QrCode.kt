package com.natepad.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCode {
    /**
     * Practical payload ceiling. QR v40 at error-correction L holds 2953 bytes, but
     * codes that dense barely scan from a phone screen — stay under it. RSA-4096
     * public keys (~3.4 KB armored) don't fit; their fingerprint QR always does.
     */
    const val MAX_PAYLOAD_BYTES = 2500

    /** Standard URI scheme for OpenPGP fingerprints (OpenKeychain-compatible). */
    const val FINGERPRINT_SCHEME = "OPENPGP4FPR:"

    fun generate(payload: String, sizePx: Int = 768): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val row = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }.getOrNull()
}
