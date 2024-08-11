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
import android.speech.RecognizerIntent
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
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

@Composable
fun AppContent() {
    ChatVisionTheme {
        val context = LocalContext.current
        val viewModel = remember { MainViewModel(context) }
        var textInput by remember { mutableStateOf("where is the cat") }

        var hasVibratePermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.VIBRATE
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            hasVibratePermission = isGranted
        }

        LaunchedEffect(Unit) {
            if (!hasVibratePermission) {
                permissionLauncher.launch(Manifest.permission.VIBRATE)
            }
        }

        val vibrator = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
        val hasVibrator = remember { vibrator.hasVibrator() }

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
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {

//                            if (hasVibrator) {
//                                if (hasVibratePermission) {
//                                    viewModel.vibrate(50)
//                                } else {
//                                    // Show a toast or some other UI indication
//                                    Toast.makeText(context, "Vibration permission not granted", Toast.LENGTH_SHORT).show()
//                                    // Optionally, request permission again
//                                    permissionLauncher.launch(Manifest.permission.VIBRATE)
//                                }
//                                viewModel.vibrate(1000)
//                            } else {
//                                // Show a toast or some other UI indication
//                                Toast.makeText(context, "Vibration not available on this device", Toast.LENGTH_SHORT).show()
//                            }
                            viewModel.beep(1000) // 1 second beep
                            viewModel.captureImage(textInput)
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
    private val viewModel: MainViewModel by viewModels()

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spokenText: String? = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
                spokenText?.let {
                    viewModel.onSpeechRecognized(it)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initLocal()) {
            // Handle initialization error
            Log.e("OpenCV", "OpenCV initialization failed")
        }
        setContent {
            AppContent()
        }
        lifecycleScope.launch {

        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        speechRecognizerLauncher.launch(intent)
    }
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
