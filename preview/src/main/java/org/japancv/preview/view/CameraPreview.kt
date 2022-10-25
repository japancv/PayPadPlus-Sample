@file:OptIn(ExperimentalCoroutinesApi::class)

package org.japancv.preview.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.japancv.preview.image.CameraImageProxy
import org.japancv.preview.image.DualAnalyzer
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class CameraPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val cameraId: String = CameraCharacteristics.LENS_FACING_BACK.toString(),
    private val enableIrCamera: Boolean = true,
): TextureView(context, attrs) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val cameraManager by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice
    private var irCamera: CameraDevice? = null

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession
    private lateinit var irSession: CameraCaptureSession

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader
    private lateinit var irImageReader: ImageReader

    private val imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }
    private var imageReaderHandler: Handler = Handler(imageReaderThread.looper)

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    /** Readers for analysis used as buffers for camera still shots */
    private lateinit var analysisImageReader: ImageReader
    private lateinit var irAnalysisImageReader: ImageReader

    private val analysisImageReaderThread = HandlerThread("AnalysisImageReaderThread").apply { start() }
    private var analysisImageReaderHandler: Handler = Handler(analysisImageReaderThread.looper)

    private val irAnalysisImageReaderThread = HandlerThread("IrAnalysisImageReaderThread").apply { start() }
    private var irAnalysisImageReaderHandler: Handler = Handler(irAnalysisImageReaderThread.looper)

    private val irCameraThread = HandlerThread("IRCameraThread").apply { start() }
    private val irCameraHandler = Handler(irCameraThread.looper)

    private val surfaceTextureCallback = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            initializeCamera(surfaceTexture)
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            destroy()
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
    }

    /** Conflated channels for sending [Image] */
    private val analysisImageSendChannel = Channel<Image>(CONFLATED)
    private val irAnalysisImageSendChannel = Channel<Image>(CONFLATED)

    private val onAnalysisImageAvailableListener =
        ImageReader.OnImageAvailableListener {
            scope.launch {
                val image = it.acquireNextImage() ?: return@launch
                analysisImageSendChannel.send(image)
                image.close()
            }
        }
    private val onIrImageAvailableListener =
        ImageReader.OnImageAvailableListener {
            scope.launch {
                val image = it.acquireNextImage() ?: return@launch
                irAnalysisImageSendChannel.send(image)
                image.close()
            }
        }

    private lateinit var analyzerProducer: ReceiveChannel<Pair<Image, Image?>>
    private var aspectRatio = 0.0f

    var analyzer: DualAnalyzer? = null
    var targetSize: Size? = null

    init {
        this.surfaceTextureListener = surfaceTextureCallback
        this.scaleX = -1f

        scope.launch(Dispatchers.IO) {
            // Producer that produces images acquired from cameras.
            // Get closed when this [CameraPreview] is destroyed
            analyzerProducer = produce {
                while(true) {
                    try {
                        val image = analysisImageSendChannel.receive()
                        val irImage = if (enableIrCamera) irAnalysisImageSendChannel.receive() else null
                        send(Pair(image, irImage))
                    } catch(e: ClosedReceiveChannelException) {
                        close()
                    }
                }
            }

            // Consumer that consumes images acquired from cameras
            while(true) {
                try {
                    val images = analyzerProducer.receive()
                    if (!enableIrCamera) analyzer?.analyze(CameraImageProxy(images.first))
                    else analyzer?.analyze(CameraImageProxy(images.first), CameraImageProxy(images.second!!))
                } catch(e: ClosedReceiveChannelException) {
                    break
                }
            }
        }

    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be
     * measured based on the ratio calculated from the parameters.
     *
     * @param width  Camera resolution horizontal size
     * @param height Camera resolution vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Size cannot be negative" }
        aspectRatio = width.toFloat() / height.toFloat()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (aspectRatio != 0.0f) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val newHeight: Int
            val newWidth: Int
            if (width / aspectRatio < height) {
                newWidth = width
                newHeight = (width / aspectRatio).roundToInt()
            } else {
                newHeight = height
                newWidth = (height * aspectRatio).roundToInt()
            }
            val newWidthSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
            val newHeightSpec = MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY)
            super.onMeasure(newWidthSpec, newHeightSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun openLight() {
        scope.launch(Dispatchers.IO) {
            try {
                File("/proc/proc_led_light").writeText("200, 0")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun closeLight() {
        scope.launch(Dispatchers.IO) {
            try {
                File("/proc/proc_led_light").writeText("0, 0")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    suspend fun takePhoto(): CombinedCaptureResult = suspendCoroutine { cont ->
        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {}

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Pair<Image, Image?>>(IMAGE_BUFFER_SIZE)
        scope.launch {
            val rgbImage = scope.async {
                suspendCoroutine {
                    imageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireNextImage()
                        Timber.d("Image available in queue: ${image.timestamp}")
                        it.resume(image)

                    }, imageReaderHandler)
                }
            }

            if (enableIrCamera) {
                val irImage = scope.async {
                    suspendCoroutine {
                        irImageReader.setOnImageAvailableListener({ reader ->
                            val image = reader.acquireNextImage()
                            Timber.d("IRImage available in queue: ${image.timestamp}")
                            it.resume(image)
                        }, imageReaderHandler)
                    }
                }

                imageQueue.add(Pair(rgbImage.await(), irImage.await()))
            } else {
                imageQueue.add(Pair(rgbImage.await(), null))
            }
        }

        val captureRequest = session.device.createCaptureRequest(
            CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
            }
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Timber.d("Capture result received: $resultTimestamp")

                // Set a timeout in case image captured is dropped from the pipeline
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                //  the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                scope.launch(cont.context) {
                    while (true) {

                        // Dequeue images while timestamps don't match
                        val images = imageQueue.take()
                        Timber.d("Matching image dequeued: ${images.first.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)
                        irImageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            val (rgb, ir) = imageQueue.take()
                            rgb.close()
                            ir?.close()
                        }

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(
                            images.first, images.second, result, imageReader.imageFormat))

                        // There is no need to break out of the loop, this coroutine will suspend
                    }
                }
            }
        }, cameraHandler)

        if (enableIrCamera && this::irSession.isInitialized) {
            irSession.device.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(irImageReader.surface)
                irSession.capture(build(), object : CameraCaptureSession.CaptureCallback() {

                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        // Delegate to `captureRequest`
                    }
                }, irCameraHandler)
            }
        }
    }

    private fun initializeCamera(texture: SurfaceTexture? = null) = scope.launch(Dispatchers.Main) {
        // Open the selected camera and IR camera
        camera = openCamera(cameraManager, cameraId, cameraHandler)

        if (enableIrCamera) {
            irCamera?.let { ic ->
                irImageReader = ImageReader.newInstance(
                    IR_IMAGE_WIDTH, IR_IMAGE_HEIGHT, ImageFormat.YUV_420_888, IMAGE_BUFFER_SIZE)
                irAnalysisImageReader = ImageReader.newInstance(
                    IR_IMAGE_WIDTH, IR_IMAGE_HEIGHT, ImageFormat.YUV_420_888, IMAGE_BUFFER_SIZE)
                irAnalysisImageReader.setOnImageAvailableListener(onIrImageAvailableListener, irAnalysisImageReaderHandler)
                irSession = createCaptureSession(ic, listOf(irAnalysisImageReader.surface, irImageReader.surface), irCameraHandler)
                ic.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW).apply {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    addTarget(irAnalysisImageReader.surface)
                }.also {
                    irSession.setRepeatingRequest(it.build(), null, irCameraHandler)
                }
            }
        }

        // Initialize an image reader which will be used to capture still photos
        val size = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.YUV_420_888)
            .filter {  size ->
                // Filter all sizes equal of less than Target size if it is existing,
                // otherwise not filtering
                targetSize?.let { it.width * it.height >= size.width * size.height } ?: true
            }
            .maxByOrNull { it.height * it.width }!!

        // We configure the size of default buffer to be the size of camera preview we want.
//        setAspectRatio(size.height, size.width)

        imageReader = ImageReader.newInstance(
            size.width, size.height, ImageFormat.YUV_420_888, IMAGE_BUFFER_SIZE)

        analysisImageReader = ImageReader.newInstance(
            size.width, size.height, ImageFormat.YUV_420_888, IMAGE_BUFFER_SIZE)
        analysisImageReader.setOnImageAvailableListener(onAnalysisImageAvailableListener, analysisImageReaderHandler)

        // Creates list of Surfaces where the camera will output frames
        val targets = mutableListOf(analysisImageReader.surface, imageReader.surface)
        texture?.let {
            val previewSurface = Surface(it)
            targets.add(previewSurface)
        }

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW).apply {
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                targets.filterNot { it == imageReader.surface }.forEach(::addTarget)
            }

        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
    }


    /**
     * Open camera by the given camera id.
     * If [enableIrCamera] is true, open IR camera.
     */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.cameraIdList.filterNot { cameraId == it }.forEach {
            if (enableIrCamera && isIrCamera(it)) {
                manager.openCamera(it, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        irCamera = camera
                    }
                    override fun onDisconnected(p0: CameraDevice) {}
                    override fun onError(p0: CameraDevice, p1: Int) {}
                }, irCameraHandler)
                openLight()
                return@forEach
            }
        }

        manager.openCamera(cameraId,
        object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cont.resume(device)
            }

            override fun onDisconnected(device: CameraDevice) {}

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    fun destroy() {
        scope.launch(Dispatchers.IO) {
            closeCamera()
            onDestroy()
        }
        scope.cancel()
    }

    private fun closeCamera() {
        try {
            irCamera?.close()?.also {
                closeLight()
            }
            camera.close()
        } catch (exc: Throwable) {
            Log.e("CameraPreview", "Error closing camera", exc)
        }
    }

    private fun onDestroy() {
        cameraThread.quitSafely()
        irCameraThread.quitSafely()
        imageReaderThread.quitSafely()
        irAnalysisImageReaderThread.quitSafely()
    }

    companion object {
        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5_000

        private const val IR_IMAGE_WIDTH = 480
        private const val IR_IMAGE_HEIGHT = 640

        private fun isIrCamera(cameraId: String): Boolean = cameraId == CameraCharacteristics.LENS_FACING_FRONT.toString()

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
            val image: Image,
            val irImage: Image?,
            val metadata: CaptureResult,
            val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }
    }
}