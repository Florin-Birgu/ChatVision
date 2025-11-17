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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.GlobalScope
import org.opencv.video.TrackerMIL
import java.util.Locale

// App state for better flow control
sealed class AppState {
    object Idle : AppState()
    data class Searching(val query: String) : AppState()
    data class Tracking(val objectName: String) : AppState()
    object TrackingLost : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel(private val context: Context) : ViewModel() {

    private var _beepInterval: Long? = null
    private val  mp: MediaPlayer = MediaPlayer.create(context,  R.raw.beep);
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    // Text-to-Speech for voice feedback
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    // App state management
    private val _appState = MutableStateFlow<AppState>(AppState.Idle)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    // Current search query
    private var currentQuery: String = ""

    // Tracking state
    private var tracker: TrackerMIL? = null
    private var trackingLostFrames = 0
    private val maxLostFrames = 30 // ~1 second at 30fps

    // Success detection
    private var centeredFrameCount = 0
    private val requiredCenteredFrames = 15 // ~0.5 seconds at 30fps
    private var successAnnounced = false

    init {
        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                ttsInitialized = true
                speak("ChatVision ready. Say 'where is' followed by an object name.")
            }
        }

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        toneGenerator.release()
        tracker?.clear()
    }

    private fun speak(text: String, priority: Int = TextToSpeech.QUEUE_FLUSH) {
        if (ttsInitialized) {
            textToSpeech?.speak(text, priority, null, text.hashCode().toString())
        }
    }

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

    /**
     * Handle recognized speech and trigger appropriate action
     */
    fun onSpeechRecognized(spokenText: String) {
        _recognizedText.value = spokenText
        val lowerText = spokenText.lowercase()

        when {
            lowerText.startsWith("where is") || lowerText.startsWith("find") -> {
                // Extract object name
                val objectName = lowerText
                    .removePrefix("where is")
                    .removePrefix("find")
                    .trim()

                if (objectName.isNotEmpty()) {
                    searchForObject(objectName)
                } else {
                    speak("What would you like me to find?")
                }
            }
            lowerText.contains("done") || lowerText.contains("got it") || lowerText.contains("found it") -> {
                // User indicates they successfully found the object
                onUserFoundObject()
            }
            lowerText.contains("cancel") || lowerText.contains("stop") -> {
                cancelSearch()
            }
            lowerText.contains("help") -> {
                provideHelp()
            }
            lowerText.contains("status") -> {
                announceStatus()
            }
        }
    }

    /**
     * Start searching for an object
     */
    fun searchForObject(query: String) {
        currentQuery = query
        _appState.value = AppState.Searching(query)
        speak("Searching for $query. Hold steady.")

        val bitmap = _rawCurrentFrame.value
        if (bitmap == null) {
            _appState.value = AppState.Error("Camera not ready")
            speak("Camera not ready. Please try again.")
            resetToIdle()
            return
        }

        viewModelScope.launch {
            val rect = detectObject(bitmap, query)
            if (rect != null) {
                _detectedRect.value = rect
                _appState.value = AppState.Tracking(query)
                speak("Found! Follow the beeps to center the $query.")

                // Initialize tracker
                previousFrame = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
                Utils.bitmapToMat(bitmap, previousFrame)
                Imgproc.cvtColor(previousFrame, previousFrame, Imgproc.COLOR_RGBA2GRAY)

                trackingLostFrames = 0
            } else {
                _appState.value = AppState.Error("Object not found")
                speak("Sorry, I couldn't find $query. Try again or look in a different area.")
                resetToIdle()
            }
        }
    }

    /**
     * User indicates they successfully found the object
     */
    fun onUserFoundObject() {
        if (_appState.value is AppState.Tracking) {
            speak("Great! You found the $currentQuery. Ready for the next search.")
            resetToIdle()
        } else {
            speak("No active tracking. Say 'where is' followed by an object name to search.")
        }
    }

    /**
     * Cancel current search/tracking and return to idle
     */
    fun cancelSearch() {
        speak("Cancelled.")
        resetToIdle()
    }

    /**
     * Reset to idle state
     */
    private fun resetToIdle() {
        viewModelScope.launch {
            delay(2000) // Give time for announcement
            _appState.value = AppState.Idle
            _detectedRect.value = null
            _beepInterval = null
            tracker?.clear()
            tracker = null
            trackingLostFrames = 0
            centeredFrameCount = 0
            successAnnounced = false
            currentQuery = ""
            speak("Ready. Say 'where is' followed by an object name.")
        }
    }

    /**
     * Provide contextual help
     */
    fun provideHelp() {
        val helpText = when (_appState.value) {
            is AppState.Idle -> {
                "Say 'where is' followed by an object name. For example: 'where is my phone' or 'where is the door'."
            }
            is AppState.Searching -> {
                "I'm searching for an object. Hold your phone steady and point it around the room. Say 'cancel' to stop."
            }
            is AppState.Tracking -> {
                "Object found! Move your phone slowly. Faster beeps mean you're pointing at the object. When you've found it, say 'done' or 'got it'. You can also say 'cancel' to stop."
            }
            is AppState.TrackingLost -> {
                "Tracking lost. Say 'where is' to search again, or 'cancel' to stop."
            }
            is AppState.Error -> {
                "There was an error. Say 'where is' followed by an object name to try again."
            }
        }
        speak(helpText)
    }

    /**
     * Announce current status
     */
    private fun announceStatus() {
        val status = when (val state = _appState.value) {
            is AppState.Idle -> "Idle. Ready to search."
            is AppState.Searching -> "Searching for ${state.query}."
            is AppState.Tracking -> "Tracking ${state.objectName}. Follow the beeps."
            is AppState.TrackingLost -> "Tracking lost."
            is AppState.Error -> "Error: ${state.message}"
        }
        speak(status)
    }

    // Keep old method for backward compatibility with button
    fun captureImage(question: String) {
        if (question.isNotBlank()) {
            searchForObject(question)
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
        // Only track if we're in tracking state
        if (_appState.value !is AppState.Tracking) {
            return
        }

        val currentFrame = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, currentFrame)
        Imgproc.cvtColor(currentFrame, currentFrame, Imgproc.COLOR_RGBA2GRAY)

        val detectedRect = _detectedRect.value
        if (detectedRect == null) {
            currentFrame.release()
            return
        }

        // Initialize tracker on first frame after detection
        if (tracker == null && previousFrame != null) {
            val roi = org.opencv.core.Rect(
                detectedRect.left,
                detectedRect.top,
                detectedRect.width(),
                detectedRect.height()
            )

            if (isValidRect(roi, previousFrame!!)) {
                tracker = TrackerMIL.create()
                try {
                    tracker!!.init(previousFrame, roi)
                    Log.d("Tracker", "Tracker initialized successfully")
                } catch (e: CvException) {
                    Log.e("Tracker", "Error initializing tracker: ${e.message}")
                    tracker = null
                }
            }
        }

        // Update tracking
        if (tracker != null && previousFrame != null) {
            val bbox = org.opencv.core.Rect()
            val success = tracker!!.update(currentFrame, bbox)

            if (success && isValidRect(bbox, currentFrame)) {
                // Tracking successful
                _detectedRect.value = Rect(bbox.x, bbox.y, bbox.x + bbox.width, bbox.y + bbox.height)
                trackingLostFrames = 0

                // Draw rectangle on frame
                Imgproc.rectangle(
                    currentFrame,
                    Point(bbox.x.toDouble(), bbox.y.toDouble()),
                    Point((bbox.x + bbox.width).toDouble(), (bbox.y + bbox.height).toDouble()),
                    Scalar(255.0, 0.0, 0.0),
                    2
                )
            } else {
                // Tracking failed
                trackingLostFrames++
                Log.w("Tracker", "Tracking lost for $trackingLostFrames frames")

                if (trackingLostFrames >= maxLostFrames) {
                    // Tracking lost for too long
                    _appState.value = AppState.TrackingLost
                    speak("Tracking lost. Say 'where is ${currentQuery}' to search again, or say 'cancel'.")
                    _beepInterval = null // Stop beeping

                    // Auto-reset after announcement
                    viewModelScope.launch {
                        delay(5000) // Give user time to respond
                        if (_appState.value is AppState.TrackingLost) {
                            resetToIdle()
                        }
                    }
                }
            }
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
        // Only beep if we're actively tracking
        if (_appState.value is AppState.Tracking) {
            _detectedRect.value?.let { rect ->
                val interval = calculateBeepInterval(rect, bitmap.width, bitmap.height)
                _beepInterval = interval

                // Check if object is centered (interval < 200ms means very close to center)
                if (interval < 200) {
                    centeredFrameCount++

                    // If object has been centered for ~0.5 seconds, announce success
                    if (centeredFrameCount >= requiredCenteredFrames && !successAnnounced) {
                        successAnnounced = true
                        speak("Perfect! You're pointing at the $currentQuery. Say 'done' when you're ready to find something else.", TextToSpeech.QUEUE_ADD)
                    }
                } else {
                    // Object moved away from center, reset counter but keep success announced flag
                    centeredFrameCount = 0
                }
            }
        } else {
            _beepInterval = null // Stop beeping when not tracking
            centeredFrameCount = 0
            successAnnounced = false
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


