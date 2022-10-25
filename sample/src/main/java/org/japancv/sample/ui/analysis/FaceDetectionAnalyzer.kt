package org.japancv.sample.ui.analysis

import android.annotation.SuppressLint
import android.media.Image
import com.nebula.irdc.sdk.AttributeDetector
import com.nebula.irdc.sdk.FaceDetector
import com.nebula.irdc.sdk.FaceTracker
import com.nebula.irdc.sdk.Interface.Orientation
import com.nebula.irdc.sdk.Interface.PixelFormat
import com.nebula.irdc.sdk.LivenessDetector
import com.nebula.irdc.sdk.Model.Quality
import com.nebula.irdc.sdk.Model.QualityList
import com.nebula.irdc.sdk.Model.TrackObject
import com.nebula.irdc.sdk.QualityDetector
import org.japancv.preview.util.toNV21
import org.japancv.sample.ui.ktx.toYuvImage
import timber.log.Timber

/**
 * FaceDetectionAnalyzer is the helper class for the analysis of
 * Face Detection, Quality Check, and Liveness Detection from the frames of camera.
 */
class FaceDetectionAnalyzer(
    private val attributeDetector: AttributeDetector? = null,
    private val faceDetector: FaceDetector? = null,
    private val faceTracker: FaceTracker? = null,
    private val livenessDetector: LivenessDetector? = null,
    private val qualityDetector: QualityDetector? = null,
) {
    private val threshold = 0.5f

    /**
     * When fetched the frames from the camera, passing the frame into this frame starts the analysis
     *
     * @param rgbImage RGB Camera frame
     * @param irImage (optional) IR Camera frame. If null then do monocular analysis.
     *
     * @return a list of track object, if the list is not empty represented passing the analysis, otherwise failed.
     */
    fun onCameraImage(rgbImage: Image, irImage: Image?): List<TrackObject> {
        val rgbYuv = rgbImage.toYuvImage()
        val irYuv = irImage?.toNV21()

        if (rgbYuv != null && irYuv != null)
            return binocularTracker(
                rgbYuv.yuvData, rgbYuv.width, rgbYuv.height, 0,
                irYuv.yuvData, irYuv.width, irYuv.height, 0,
            )

        if (rgbYuv != null && irYuv == null) {
            return monocularTracker(rgbYuv.yuvData, rgbYuv.width, rgbYuv.height, 0)
        }
        throw IllegalStateException("Not receiving right images")
    }

    private fun monocularTracker(
        rgbByteBuffer: ByteArray,
        width: Int,
        height: Int,
        stride: Int): List<TrackObject> {
        val orientation = Orientation.UP
        val rgbTargetApiResult = faceTracker?.track(
            rgbByteBuffer,
            PixelFormat.NV21,
            width,
            height,
            stride.toLong(),
            orientation)
        if (rgbTargetApiResult?.status?.isSuccess == false) {
            Timber.e( "api result %s", rgbTargetApiResult.status)
            return listOf()
        }

        rgbTargetApiResult?.let { rgbApiResult ->
            if (rgbApiResult.result!!.trackObjectList.cnt <= 0) {
                Timber.i(rgbTargetApiResult.result!!.trackObjectList.cnt.toString())
                return listOf()
            }
            Timber.i("rgb result: %s", rgbApiResult.result!!.trackObjectList.toJson())

            val qualityRst = qualityDetector?.detect(
                rgbByteBuffer,
                PixelFormat.NV21,
                width,
                height,
                stride.toLong(),
                orientation,
                rgbApiResult.result!!
            )

            if (qualityRst?.status?.isSuccess == false) {
                Timber.e(
                    "quality detect failed, error code: %s",
                    rgbApiResult.status.code.toString()
                )
                return listOf()
            }

            qualityRst?.let { qualityApiResult ->
                val trackObjectList = rgbApiResult.result!!.trackObjectList.objects
                val qualityList = qualityApiResult.result!!.qualities
                if (trackObjectList.size != qualityList.size) {
                    return listOf()
                }

                val filtered = trackObjectList.filterIndexed { index, _ ->
                    qualityList[index].quality >= threshold
                }

                return filtered
            }
        }

        return listOf()
    }

    private fun binocularTracker(
        rgbByteBuffer: ByteArray,
        rgbWidth: Int,
        rgbHeight: Int,
        rgbStride: Int,
        irByteBuffer: ByteArray,
        irWidth: Int,
        irHeight: Int,
        irStride: Int
    ): List<TrackObject> {
        val orientation = Orientation.UP
        val rgbTargetApiResult = faceTracker?.track(
            rgbByteBuffer,
            PixelFormat.NV21,
            rgbWidth,
            rgbHeight,
            rgbStride.toLong(),
            orientation)
        if (rgbTargetApiResult?.status?.isSuccess == false) {
            Timber.e( "api result %s", rgbTargetApiResult.status)
            return listOf()
        }

        rgbTargetApiResult?.let { faceTrackRst ->
            if (faceTrackRst.result!!.trackObjectList.cnt <= 0) {
                Timber.i(rgbTargetApiResult.result!!.trackObjectList.cnt.toString())
                return listOf()
            }
            Timber.i("rgb result: %s", faceTrackRst.result!!.trackObjectList.toJson())

            val rgbQualityRst = qualityDetector?.detect(
                rgbByteBuffer,
                PixelFormat.NV21,
                rgbWidth,
                rgbHeight,
                rgbStride.toLong(),
                orientation,
                faceTrackRst.result!!
            )

            if (rgbQualityRst?.status?.isSuccess == false) {
                Timber.e(
                    "quality detect failed, error code: %s",
                    faceTrackRst.status.code.toString()
                )
                return listOf()
            }

            rgbQualityRst?.let { qualityApiResult ->
                val trackObjectList = faceTrackRst.result!!.trackObjectList.objects
                val qualityList = qualityApiResult.result!!.qualities
                if (trackObjectList.size != qualityList.size) {
                    return listOf()
                }

                val filtered = trackObjectList.filterIndexed { index, _ ->
                    qualityList[index].quality >= threshold
                }

                faceDetector?.detect(irByteBuffer, PixelFormat.NV21, irWidth, irHeight, irStride.toLong(), orientation)?.let { faceDetectionRst ->
                    if (!faceDetectionRst.status.isSuccess) {
                        Timber.e("ir detect failed, code: %s", faceDetectionRst.status.code.toString())
                        return listOf()
                    }
                    if ((faceDetectionRst.result?.trackObjectList?.cnt ?: 0) <= 0) {
                        return listOf()
                    }

                    qualityDetector?.detect(
                        irByteBuffer,
                        PixelFormat.NV21,
                        irWidth,
                        irHeight,
                        irStride.toLong(),
                        orientation,
                        faceDetectionRst.result
                    )?.let { irQualityRst ->
                        if (!irQualityRst.status.isSuccess) {
                            Timber.e("quality detect failed, error code: %s", irQualityRst.status.code.toString())
                            return listOf()
                        }

                        val rgbQualityList = QualityList(arrayOf(rgbQualityRst.result!!.qualities[0]), 1)
                        val irQualityList = QualityList(arrayOf(irQualityRst.result!!.qualities[0]), 1)

                        // Use the result of the quality detection from RGB camera frame and IR camera frame to do Liveness detection
                        livenessDetector?.detect(
                            rgbByteBuffer,
                            irByteBuffer,
                            PixelFormat.NV21,
                            rgbWidth,
                            rgbHeight,
                            rgbStride.toLong(),
                            orientation,
                            rgbQualityList,
                            irQualityList
                        )?.let { livenessDetectionRst ->
                            if (!livenessDetectionRst.status.isSuccess) {
                                Timber.e("liveness detect failed, error code: %s", irQualityRst.status.code.toString())
                                return listOf()
                            }

                            livenessDetectionRst.result?.let { result ->
                                if (result.cnt > 0) {
                                    val livenessResultScore = result.livenessResults[0].score
                                    return if (livenessResultScore <= 0.95) {
                                        // Passed the liveness detection
                                        filtered
                                    } else {
                                        // Failed the liveness detection
                                        listOf()
                                    }
                                }

                            } ?: return listOf()
                        }
                    }
                }
            }
        }

        return listOf()
    }

    @SuppressLint("BinaryOperationInTimber")
    private fun filterRules(trackObject: TrackObject, quality: Quality): Boolean {
        if (trackObject.rect.height() < 60 || trackObject.rect.width() < 60) {
            Timber.d("filter rect: h: " + trackObject.rect.height().toString() + " w: " + trackObject.rect.width()
                    .toString())
            return false
        }
        if (trackObject.confidence < 0) return false
        if (quality.quality < 0.9) {
            Timber.d(quality.quality.toString())
            return false
        }
        if (quality.keypoints_confidence < 0.89) {
            Timber.d("filter get keypoint confidence: " + quality.keypoints_confidence.toString())
            return false
        }
        if (quality.clarity < 0.3) {
            Timber.d("filter getClarity: " + quality.clarity.toString())
            return false
        }
        if (quality.headPose.yaw > 30 || quality.headPose.yaw < -30) {
            Timber.d("filter getYaw: " + quality.headPose.yaw.toString())
            return false
        }
        if (quality.headPose.pitch > 30 || quality.headPose.pitch < -30) {
            Timber.d("filter getPitch: " + quality.headPose.pitch.toString())
            return false
        }
        if (quality.headPose.roll > 30 || quality.headPose.roll < -30) {
            Timber.d("filter getRoll: " + quality.headPose.roll.toString())
            return false
        }
        return true
    }
}