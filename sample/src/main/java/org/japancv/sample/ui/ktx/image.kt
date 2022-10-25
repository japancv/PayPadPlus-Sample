package org.japancv.sample.ui.ktx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Base64
import org.japancv.preview.util.rotateAndMirror
import org.japancv.preview.util.toNV21
import java.io.ByteArrayOutputStream

fun Bitmap.toBase64(): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    val base64Str: String = Base64.encodeToString(byteArray, Base64.DEFAULT)
    return base64Str.replace(System.getProperty("line.separator"), "")
}

private const val QUALITY_OF_JPEG_COMPRESSION = 96

fun YuvImage.toBitmap(targetRect: Rect? = null): Bitmap? {
    val out = ByteArrayOutputStream()
    compressToJpeg(targetRect ?: Rect(0, 0, width, height), QUALITY_OF_JPEG_COMPRESSION, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

/** Convert [Image] to Yuv image that rotate 90 degree and horizontal mirror  */
fun Image.toYuvImage() = toNV21()?.rotateAndMirror()