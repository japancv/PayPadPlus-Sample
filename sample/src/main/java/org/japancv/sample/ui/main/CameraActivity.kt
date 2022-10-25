package org.japancv.sample.ui.main

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.japancv.sample.databinding.ActivityCameraBinding
import org.japancv.sample.ui.BaseActivity
import org.japancv.sample.ui.analysis.FaceDetectionAnalyzer
import org.japancv.sample.ui.ktx.toBitmap
import org.japancv.sample.ui.ktx.toYuvImage
import org.japancv.sample.ui.ktx.viewBinding
import org.japancv.sdk.startup.SdkInitializer

/**
 * In this Activity, it demonstrates the steps of using Face Detection SDK and using server-side 1:N Face Recognition.
 */
class CameraActivity : BaseActivity() {
    private val binding by viewBinding(ActivityCameraBinding::inflate)
    private val viewModel: FaceRecognitionViewModel by viewModels()
    private lateinit var faceDetectionAnalyzer: FaceDetectionAnalyzer

    init {
        lifecycleScope.launchWhenStarted {
            // Initial SDK and initiate analyzer
            val sdkModule = SdkInitializer().create(this@CameraActivity)
            faceDetectionAnalyzer = FaceDetectionAnalyzer(
                attributeDetector = sdkModule.attributeDetector,
                faceDetector = sdkModule.faceDetector,
                faceTracker = sdkModule.faceTracker,
                livenessDetector = sdkModule.livenessDetector,
                qualityDetector = sdkModule.qualityDetector,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // 1. When click the take photo button, it triggers the Face Recognition Process
        binding.photoBtn.setOnClickListener {
            it.isEnabled = false
            binding.takePhotoProgressBar.visibility = View.VISIBLE
            binding.boundingBox.visibility = View.INVISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                // 2. Take a photo: Fetch the camera frames (RGB camera and IR camera)
                binding.preview.takePhoto().use { result ->

                    // 3. Use the camera frames to do the Face detection analysis. If detected, returns non-empty object list.
                    val trackObjects = faceDetectionAnalyzer.onCameraImage(result.image, result.irImage)

                    if (trackObjects.isNotEmpty()) {
                        // Create the Face Rect which can be used to crop the face from Camera frame
                        // Plus the padding makes the face image bigger as the Face Detection result it only contains the face landmarks
                        val faceRect =
                            Rect(trackObjects[0].rect.left - PADDING, trackObjects[0].rect.top - PADDING,
                                trackObjects[0].rect.right + PADDING, trackObjects[0].rect.bottom + PADDING)

                        // 4. Use the cropped face image sending to Anysee for server-side 1:N Face Recognition
                        result.image.toYuvImage()?.toBitmap(faceRect)?.let { faceBitmap ->
                            viewModel.searchFace(faceBitmap)
                        }
                    } else {
                        navigateToResult(Result.failure<Unit>(Throwable()))
                    }
                }

                // Re-enable click listener after photo is taken
                it.post { it.isEnabled = true }
            }
        }

        // According to the result of 1:N Face Recognition navigate the result to the corresponding page
        lifecycleScope.launch {
            viewModel.resultSharedFlow.collect {
                navigateToResult(it)
            }
        }
    }

    /**
     * Navigate to result
     */
    private fun navigateToResult(result: Result<*>) {
        lifecycleScope.launch(Dispatchers.Main) {
            startActivity(Intent(
                this@CameraActivity,
                if (result.isSuccess) WelcomeActivity::class.java
                else FailureActivity::class.java
            ))
            finish()
        }
    }

    companion object {
        private const val PADDING = 30
    }
}
