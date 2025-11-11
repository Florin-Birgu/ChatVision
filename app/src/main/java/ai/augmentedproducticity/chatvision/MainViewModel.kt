package ai.augmentedproducticity.chatvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
//import org.opencv.video.TrackerMIL
//import org.opencv.video.TrackerVit
//import org.opencv.video.TrackerDaSiamRPN
//import org.opencv.video.TrackerGOTURN
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import ai.augmentedproducticity.chatvision.ImageUtils;
import ai.augmentedproducticity.chatvision.ImageUtils.bitmapToByteArray
import ai.augmentedproducticity.chatvision.ImageUtils.byteArrayToBitmap
import ai.augmentedproducticity.chatvision.ImageUtils.rotateBitmap
import ai.augmentedproducticity.chatvision.ImageUtils.toBase64
import android.media.MediaPlayer
import kotlinx.coroutines.GlobalScope
import org.opencv.video.TrackerMIL


class MainViewModel(private val context: Context) : ViewModel() {

    private var _beepInterval: Long? = null
    private val  mp: MediaPlayer = MediaPlayer.create(context,  R.raw.beep);
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    fun beep() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
//        mp.start();
    }

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    private val _rawCurrentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _detectedRect = MutableStateFlow<Rect?>(null)
    val detectedRect: StateFlow<Rect?> = _detectedRect.asStateFlow()

    // Previous frame for tracking
    private var previousFrame: Mat? = null

    var previewView: PreviewView? = null
//    private var imageCapture: ImageCapture? = null

    fun initializeCamera(context: ComponentActivity) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(context, cameraProvider)
        }, ContextCompat.getMainExecutor(context))

        GlobalScope.launch {
            while (true){
                // Short beep
                _beepInterval?.let {
                    delay(it)
                    beep()
                }
            }
        }
    }

    private fun bindCameraUseCases(context: ComponentActivity, cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView?.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA


        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
//                    .setResolutionStrategy(
//                        ResolutionStrategy(
//                            Size(720, 1200),
//                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
//                        )
//                    )
                    .build()
            )
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        //implementationMode


        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            this.onFrameCaptured(imageProxy)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("Camera", "Use case binding failed", exc)
        }
    }

    fun captureImage(question: String) {
        val bitmap = _rawCurrentFrame.value ?: return
        viewModelScope.launch {
            val rect = detectObject(bitmap, question)
            rect?.let {
                _detectedRect.value = it
                previousFrame = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
                Utils.bitmapToMat(bitmap, previousFrame)
                Imgproc.cvtColor(previousFrame, previousFrame, Imgproc.COLOR_RGBA2GRAY)
            }
        }
    }
    private suspend fun detectObject(bitmap: Bitmap, question: String): Rect? {
    try {
        // Get API key from secure storage
        val securePrefs = SecurePreferences(context)
        val apiKey = securePrefs.getGeminiApiKey()

        if (apiKey.isNullOrBlank()) {
            Log.e("AI", "No API key configured")
            return null
        }

        val generationConfig = generationConfig{
//            stopSequences = listOf("\n")
            temperature = 0.7f
            topK = 50
            topP = 0.9f
            maxOutputTokens = 1024
        }

        val model = GenerativeModel(
            modelName = "gemini-1.5-flash-001",
            apiKey = apiKey,
            generationConfig = generationConfig
        )

        val content = """
            You are an object detection expert.
            It is crucial you get it right.
            Find object in image. Top left of the image is [0, 0].
            Reply only with the coordinates of the object's bounding box in the format [x1, y1, x2, y2].
            Where (x1, y1) is the top-left corner and (x2, y2) is the bottom-right corner.
            Reply "null" if no object is found.
        """.trimIndent()+question;
//        val content = """
//            What is the resolution of this image? reply with box size there the Top left of the image is [0, 0].
//        """.trimIndent();
        val scope = CoroutineScope(Dispatchers.IO)

//        bitmap.density = Bitmap.DENSITY_NONE

        return withContext(Dispatchers.IO) {
            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(content)
                }
            )

            val textSplit = response.text!!.split("[", ",", "]")
            if (textSplit.size == 6) {
                val x1 = textSplit[1].trim().toIntOrNull()
                val y1 = textSplit[2].trim().toIntOrNull()
                val x2 = textSplit[3].trim().toIntOrNull()
                val y2 = textSplit[4].trim().toIntOrNull()

//                val left = y1!!
//                val top = x1!!
//                val right = y2!!
//                val bottom = x2!!

                val left = y1!!/2.25 //weird gemini bug maybe
                val top = x1!!/1.5625
                val right = y2!!/2.25
                val bottom = x2!!/1.5625


//                val left =10
//                val top = 10
//                val right =200
//                val bottom = 100

                if (x1 != null && y1 != null && x2 != null && y2 != null) {

                    return@withContext Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                }
            }
            null
        }
        } catch (e: Exception) {
            Log.e("AI", "Error processing image", e)
        }

        return null
    }

//    fun onSpeechRecognized(spokenText: String) {
//        // Handle the recognized speech text and trigger the object detection
//        viewModelScope.launch {
//            previewView?.bitmap?.let { bitmap ->
//                detectObject(bitmap, spokenText)
//            }
//        }
//    }


    fun processFrameForTracking(bitmap: Bitmap) {
        val currentFrame = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, currentFrame)
        Imgproc.cvtColor(currentFrame, currentFrame, Imgproc.COLOR_RGBA2GRAY)

        if (previousFrame == null) {
            previousFrame = currentFrame.clone()
            return
        }

        val detectedRect = _detectedRect.value
        if (detectedRect == null) {
            previousFrame?.release()
            previousFrame = currentFrame.clone()
            return
        }

        // Ensure ROI is within the bounds of the image
        val roi = org.opencv.core.Rect(
            detectedRect.left,
            detectedRect.top,
            detectedRect.width(),
            detectedRect.height()
        )


        // Add bounds check
        if (!isValidRect(roi, currentFrame)) {
            Log.e("Tracker", "Invalid ROI: $roi")
            previousFrame?.release()
            previousFrame = currentFrame.clone()
            return
        }

        val tracker = TrackerMIL.create()
        try {
            tracker.init(previousFrame, roi)
        } catch (e: CvException) {
            Log.e("Tracker", "Error initializing tracker: ${e.message}")
            previousFrame?.release()
            previousFrame = currentFrame.clone()
            return
        }

        val bbox = org.opencv.core.Rect()
        val success = tracker.update(currentFrame, bbox)
        if (success) {
            _detectedRect.value = Rect(bbox.x, bbox.y, bbox.x+bbox.width, bbox.y+bbox.height)
            Imgproc.rectangle(
                currentFrame,
                Point(bbox.x.toDouble(), bbox.y.toDouble()),
                Point((bbox.x + bbox.width).toDouble(), (bbox.y + bbox.height).toDouble()),
                Scalar(255.0, 0.0, 0.0),
                2
            )
        }

        previousFrame?.release()
        previousFrame = currentFrame.clone()

        Utils.matToBitmap(currentFrame, bitmap)
        _currentFrame.value = bitmap
    }

    private fun isValidRect(rect: org.opencv.core.Rect, mat: Mat): Boolean {
        return rect.x >= 0 &&
                rect.y >= 0 &&
                rect.x + rect.width <= mat.cols() &&
                rect.y + rect.height <= mat.rows()
    }

    fun Bitmap.toMat(): Mat {
        val mat = Mat(this.height, this.width, CvType.CV_8UC4)
        Utils.bitmapToMat(this, mat)
        return mat
    }

    // Handle new frame
    fun onFrameCaptured(image: ImageProxy) {
        val bitmap = image.toBitmap()
//        bitmap.density = Bitmap.DENSITY_NONE
        val byteArray = bitmapToByteArray(bitmap, Bitmap.CompressFormat.PNG)
//
        val bitmap2 =byteArrayToBitmap(byteArray)

        val rotatedBitmap = rotateBitmap(bitmap2!!, 90f)
        _currentFrame.value = rotatedBitmap
        _rawCurrentFrame.value = rotatedBitmap.copy(rotatedBitmap.getConfig(), rotatedBitmap.isMutable);
//        processFrameForTracking(rotatedBitmap)
        processFrameAndBeep(rotatedBitmap)
//        _currentFrame.value = bitmap
        image.close() // todo check if this is needed
    }

    fun processFrameAndBeep(bitmap: Bitmap) {
        processFrameForTracking(bitmap)
        detectedRect?.let { rect ->
            _beepInterval = rect.value?.let { calculateBeepInterval(it, bitmap.width, bitmap.height) }
        }
    }

    private fun calculateBeepInterval(rect: Rect, width: Int, height: Int): Long {
        val centerX = width / 2
        val centerY = height / 2
        val maxDimension = maxOf(width, height)

        val distanceX = abs(rect.exactCenterX().minus(centerX))
        val distanceY = abs(rect.exactCenterY().minus(centerY))
        val maxDistance = maxOf(distanceX, distanceY)

        // Map the distance to a beep interval between 100ms and 1000ms
        return ((900 * maxDistance) / (maxDimension / 2) + 100).toLong().coerceIn(100, 1000)
    }
}


