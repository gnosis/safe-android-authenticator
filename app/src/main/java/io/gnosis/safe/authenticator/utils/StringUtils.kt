package io.gnosis.safe.authenticator.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

fun String.asMiddleEllipsized(boundariesLength: Int): String {
    return if (this.length > boundariesLength * 2)
        "${this.subSequence(0, boundariesLength)}...${this.subSequence(this.length - boundariesLength, this.length)}"
    else this
}

fun String.generateQrCode(width: Int, height: Int, backgroundColor: Int = Color.WHITE, options: Map<EncodeHintType, String>? = null): Bitmap {
    val writer = QRCodeWriter()
    try {
        val bitMatrix = writer.encode(this, BarcodeFormat.QR_CODE, width, height, options)
        val bmp = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else backgroundColor)
            }
        }
        return bmp
    } catch (e: WriterException) {
        throw e
    }
}