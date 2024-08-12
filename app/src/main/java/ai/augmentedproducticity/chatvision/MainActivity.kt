package ai.augmentedproducticity.chatvision

import ai.augmentedproducticity.chatvision.ui.theme.ChatVisionTheme
import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

@Composable
fun AppContent(viewModel: MainViewModel) {
    ChatVisionTheme {
        val context = LocalContext.current
        var textInput by remember { mutableStateOf("where is the cat") }
        val recognizedText by viewModel.recognizedText.collectAsState()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CameraPreviewView(viewModel)
                    }
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Enter your question") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Recognized Speech: $recognizedText",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.beep()
                            viewModel.captureImage(recognizedText)
                        }) {
                            Text("Take Picture")
                        }
                    }
                }
                // Handle Permissions
                CameraPermissionRequest {
                    viewModel.initializeCamera(context as ComponentActivity)
                }
            }
        }
    }
}

@Composable
fun CameraPreviewView(viewModel: MainViewModel) {
    val currentFrame by viewModel.currentFrame.collectAsState()
    val detectedRect by viewModel.detectedRect.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                currentFrame?.let {
                    imageView.setImageBitmap(it)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

//        detectedRect?.let { rect ->
//            Canvas(modifier = Modifier.fillMaxSize()) {
//                drawRect(
//                    color = Color.Red,
//                    topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
//                    size = Size(rect.width().toFloat(), rect.height().toFloat()),
//                    style = Stroke(width = 4f)
//                )
//            }
//        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false


    class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initializeSpeechRecognizer()
            startContinuousListening()
        } else {
            Toast.makeText(this, "Speech recognition permission denied", Toast.LENGTH_SHORT).show()
        }
    }


    private fun handleSpeechResult(spokenText: String) {
        when {
            spokenText.toLowerCase().startsWith("where is") -> {
                viewModel.captureImage(spokenText)
            }
            spokenText.toLowerCase() == "cancel search" -> {
                // Handle canceling the search
                // Add any additional logic for canceling the search
            }
        }
    }



//    private val speechRecognizerLauncher =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == RESULT_OK) {
//                val spokenText: String? = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
//                spokenText?.let {
//                    viewModel.onSpeechRecognized(it)
//                }
//            }
//        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this, MainViewModelFactory(applicationContext))
            .get(MainViewModel::class.java)

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "OpenCV initialization failed")
        }
        setContent {
            AppContent(viewModel)
        }

        checkAndRequestAudioPermission()

        lifecycleScope.launch {
            // Your existing code here
        }
    }

    private fun checkAndRequestAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeSpeechRecognizer()
                startContinuousListening()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { spokenText ->
                    runOnUiThread {
                        handleSpeechResult(spokenText)
                    }
                }
                startContinuousListening()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e("SpeechRecognizer", "Error: $error")
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Toast.makeText(this@MainActivity, "Microphone permission is required", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        startContinuousListening()
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startContinuousListening() {
        if (!isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
            isListening = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

//    private fun startSpeechRecognition() {
//        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
//        }
//        speechRecognizerLauncher.launch(intent)
//    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionRequest(onGranted: () -> Unit) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val context = LocalContext.current

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            onGranted()
        } else {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (!cameraPermissionState.status.isGranted) {
        Text(text = "Camera permission is required for this app to function.")
    }
}
