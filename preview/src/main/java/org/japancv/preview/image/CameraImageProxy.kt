package org.japancv.preview.image

import android.annotation.SuppressLint
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import java.nio.ByteBuffer

class CameraImageProxy(private val internalImage: Image): ImageProxy {
    override fun close() {}

    override fun getCropRect(): Rect = internalImage.cropRect

    override fun setCropRect(rect: Rect?) {
        rect?.let {
            internalImage.cropRect = it
        }
    }

    override fun getFormat(): Int = internalImage.format

    override fun getHeight(): Int = internalImage.height

    override fun getWidth(): Int = internalImage.width

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> =
        internalImage.planes.map {
            CameraPlaneProxy(it)
        }.toTypedArray()

    override fun getImageInfo(): ImageInfo = CameraImageInfo(internalImage)

    @SuppressLint("UnsafeOptInUsageError")
    override fun getImage() = internalImage

    class CameraPlaneProxy(private val planes: Image.Plane): ImageProxy.PlaneProxy {
        override fun getRowStride(): Int = planes.rowStride
        override fun getPixelStride(): Int = planes.pixelStride
        override fun getBuffer(): ByteBuffer = planes.buffer
    }

    @SuppressLint("RestrictedApi")
    class CameraImageInfo(private val internalImage: Image): ImageInfo {
        override fun getTagBundle(): TagBundle {
            return TagBundle.emptyBundle()
        }

        override fun getTimestamp(): Long = internalImage.timestamp

        override fun getRotationDegrees(): Int = 0

        override fun populateExifData(exifBuilder: ExifData.Builder) {

        }
    }
}