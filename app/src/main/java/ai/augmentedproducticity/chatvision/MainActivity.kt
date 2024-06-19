package ai.augmentedproducticity.chatvision

import ai.augmentedproducticity.chatvision.ui.theme.ChatVisionTheme
import android.Manifest
import android.graphics.Bitmap
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun AppContent() {
    ChatVisionTheme {
        val context = LocalContext.current
        val viewModel = MainViewModel()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                CameraPreviewView(viewModel)
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
    AndroidView(factory = { context ->
        val previewView = PreviewView(context).apply {
            this.scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        viewModel.previewView = previewView
        previewView
    })
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
        setContent {
            AppContent()
        }
        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
//        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
//            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
//        }
//        speechRecognizerLauncher.launch(intent)
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

//// Extension functions to convert ImageProxy to Bitmap and Bitmap to Base64
//fun ImageProxy.toBitmap(): Bitmap {
//    // TODO: Convert ImageProxy to Bitmap
//    // Placeholder code
//    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//}
//
//fun Bitmap.toBase64(): String {
//    // TODO: Convert Bitmap to Base64 string
//    // Placeholder code
//    return ""
//}
