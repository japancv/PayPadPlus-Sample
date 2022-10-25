@file:JvmName("SdkModule")

package org.japancv.sdk

import android.content.Context
import com.nebula.irdc.sdk.AttributeDetector
import com.nebula.irdc.sdk.FaceAPI
import com.nebula.irdc.sdk.FaceDetector
import com.nebula.irdc.sdk.FaceTracker
import com.nebula.irdc.sdk.LivenessDetector
import com.nebula.irdc.sdk.QualityDetector
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Use [org.japancv.sdk.startup.SdkInitializer] to initialize SdkModule it initiates detectors with configuration.
 */
class SdkModule private constructor() {

    lateinit var faceDetector: FaceDetector
        private set
    lateinit var faceTracker: FaceTracker
        private set
    lateinit var qualityDetector: QualityDetector
        private set
    lateinit var livenessDetector: LivenessDetector
        private set
    lateinit var attributeDetector: AttributeDetector
        private set

    private var isInit = AtomicBoolean(false)

    //init SDK
    internal fun initSDK(context: Context) {
        if (isInit.get()) return

        Timber.i("initSDK")
        try {
            FileUtils.getFileContent(context, CONFIG_ASSET_PATH)?.apply {
                setAbsModelPath(this, context)?.let {
                    Timber.d("initSDK: Configuration loaded")
                    val faceDetectorApiResult = FaceAPI.createFaceDetector(it)
                    if (faceDetectorApiResult.status.isSuccess) {
                        faceDetector = faceDetectorApiResult.result!!
                    } else {
                        return
                    }
                    val faceTrackerApiResult = FaceAPI.createFaceTracker(it)
                    if (faceTrackerApiResult.status.isSuccess) {
                        faceTracker = faceTrackerApiResult.result!!
                    } else {
                        return
                    }
                    val qualityDetectorApiResult = FaceAPI.createQualityDetector(it)
                    if (qualityDetectorApiResult.status.isSuccess) {
                        qualityDetector = qualityDetectorApiResult.result!!
                    } else {
                        return
                    }
                    val livenessDetectorApiResult = FaceAPI.livenessCreate(it)
                    if (livenessDetectorApiResult.status.isSuccess) {
                        livenessDetector = livenessDetectorApiResult.result!!
                    } else {
                        return
                    }

                    val attributeDetectorApiResult = FaceAPI.createAttributeDetector(it)
                    if (attributeDetectorApiResult.status.isSuccess) {
                        attributeDetector = attributeDetectorApiResult.result!!
                    }

                    isInit.set(true)
                    Timber.i("initSDK: initialized")
                }
            }
        } catch (e: Exception) {
            Timber.e("initSDK error: %s", e.message)
        }
    }

    private fun setAbsModelPath(config: String, context: Context): String? {
        try {
            val jsonObject = JSONObject(config)
            val models = jsonObject.getJSONObject("models")
            // face
            val face = models.getJSONObject("face")
            var model = face.getString("model")
            var path = FileUtils.getAssertFilePath(context, model)
            face.put("model", path)
            models.put("face", face)
            // face august
            val faceAugust = models.getJSONObject("face_august")
            model = faceAugust.getString("model")
            path = FileUtils.getAssertFilePath(context, model)
            faceAugust.put("model", path)
            models.put("face_august", faceAugust)
            // aligner
            val aligner = models.getJSONObject("aligner")
            model = aligner.getString("model")
            path = FileUtils.getAssertFilePath(context, model)
            aligner.put("model", path)
            models.put("aligner", aligner)
            // headpose
            val headpose = models.getJSONObject("headpose")
            model = headpose.getString("model")
            path = FileUtils.getAssertFilePath(context, model)
            headpose.put("model", path)
            models.put("headpose", headpose)
            // blur
            val blur = models.getJSONObject("blur")
            model = blur.getString("model")
            path = FileUtils.getAssertFilePath(context, model)
            blur.put("model", path)
            models.put("blur", blur)
            // face_feature
            val faceFeature = models.getJSONObject("face_feature")
            model = faceFeature.getString("model")
            path = FileUtils.getAssertFilePath(context, model)
            faceFeature.put("model", path)
            models.put("face_feature", faceFeature)
            // liveness
            val liveness = models.getJSONObject("liveness")
            model = liveness.getString("model")
            path = FileUtils.getAssertFilePath(context, model)
            liveness.put("model", path)
            models.put("liveness", liveness)
            // face_attribute
            val faceAttribute = models.getJSONObject("face_attribute")
            model = faceAttribute.getString("model")
            path = FileUtils.getAssertFilePath(context, model)
            faceAttribute.put("model", path)
            models.put("face_attribute", faceAttribute)
            jsonObject.put("models", models)
            return jsonObject.toString()
        } catch (e: Exception) {
            Timber.e("setAbsModelPath error %s", e.message)
        }
        return null
    }

    companion object {
        private const val CONFIG_ASSET_PATH = "config/detect_quality_feature_all_in_one.json"

        /**
         * The singleton instance of [SdkModule]
         */
        val instance: SdkModule by lazy { SdkModule() }
    }
}