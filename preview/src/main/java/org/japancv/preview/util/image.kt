package org.japancv.preview.util

import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException
import kotlin.experimental.inv

private fun convertToNV21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 4
    val nv21 = ByteArray(ySize + uvSize * 2)
    val yBuffer: ByteBuffer = image.planes[0].buffer // Y
    val uBuffer: ByteBuffer = image.planes[1].buffer // U
    val vBuffer: ByteBuffer = image.planes[2].buffer // V
    var rowStride = image.planes[0].rowStride
    assert(image.planes[0].pixelStride == 1)
    var pos = 0
    if (rowStride == width) { // likely
        yBuffer.get(nv21, 0, ySize)
        pos += ySize
    } else {
        var yBufferPos = -rowStride // not an actual position
        while (pos < ySize) {
            yBufferPos += rowStride
            yBuffer.position(yBufferPos)
            yBuffer.get(nv21, pos, width)
            pos += width
        }
    }
    rowStride = image.planes[2].rowStride
    val pixelStride = image.planes[2].pixelStride
    assert(rowStride == image.planes[1].rowStride)
    assert(pixelStride == image.planes[1].pixelStride)
    if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
        val savePixel: Byte = vBuffer.get(1)
        try {
            vBuffer.put(1, savePixel.inv())
            if (uBuffer.get(0) == savePixel.inv()) {
                vBuffer.put(1, savePixel)
                vBuffer.position(0)
                uBuffer.position(0)
                vBuffer.get(nv21, ySize, 1)
                uBuffer.get(nv21, ySize + 1, uBuffer.remaining())
                return nv21
            }
        } catch (ignored: ReadOnlyBufferException) {
        }
        vBuffer.put(1, savePixel)
    }
    for (row in 0 until height / 2) {
        for (col in 0 until width / 2) {
            val vuPos = col * pixelStride + row * rowStride
            nv21[pos++] = vBuffer.get(vuPos)
            nv21[pos++] = uBuffer.get(vuPos)
        }
    }
    return nv21
}

fun Image.toNV21(): YuvImage? {
    require(format == ImageFormat.YUV_420_888) { "Invalid image format" }
    if (planes.size < 3) return null
    val nv21 = convertToNV21(this)
    return YuvImage(
        nv21, ImageFormat.NV21, width, height,  /* strides= */null
    )
}

fun YuvImage.rotateAndMirror(): YuvImage {
    val output = YuvImage(
        ByteArray(this.yuvData.size),
        ImageFormat.NV21,
        this.height,
        this.width,
        null
    )
    nv21Rotate90(this.yuvData, output.yuvData, this.width, this.height)
    val mirrorOutput = YuvImage(
        ByteArray(this.yuvData.size),
        ImageFormat.NV21,
        this.height,
        this.width,
        null
    )
    nv21Mirror(output.yuvData, mirrorOutput.yuvData, this.height, this.width)
    return mirrorOutput
}

/**
 * Borrowed from https://titanwolf.org/Network/Articles/Article?AID=720b0369-712f-4547-b5cc-4a5d8945cc60
 * Note, this only rotate 90 degree
 */
fun nv21Rotate90(input: ByteArray, output: ByteArray, width: Int, height: Int) {
    var nWidth = 0
    var nHeight = 0
    var wh = 0
    var uvHeight = 0

    if (width != nWidth || height != nHeight) {
        nWidth = width
        nHeight = height
        wh = nWidth * nHeight
        uvHeight = height shr  1
    }

    var k = 0
    for (i in 0 until width) {
        var nPos = 0
        for (j in 0 until height) {
            output[k] = input[nPos + i]
            k++
            nPos += width
        }
    }

    for (i in 0 until width step 2) {
        var nPos = wh
        for (j in 0 until uvHeight) {
            output[k] = input[nPos + i]
            output[k + 1] = input[nPos + i + 1]
            k += 2
            nPos += width
        }
    }
}

fun nv21Mirror(input: ByteArray, output: ByteArray, width: Int, height: Int) {
    var left: Int
    var right: Int
    var startPos = 0
    for (i in 0 until height) {
        left = startPos
        right = startPos + width - 1
        while (left < right) {
            output[left] = input[right]
            output[right] = input[left]
            left++
            right--
        }
        startPos += width
    }
    val offset = width * height
    startPos = 0
    for (i in 0 until height / 2) {
        left = offset + startPos
        right = offset + startPos + width - 2
        while (left < right) {
            output[left] = input[right]
            output[right] = input[left]
            left++
            right--
            output[left] = input[right]
            output[right] = input[left]
            left++
            right--
        }
        startPos += width
    }
}