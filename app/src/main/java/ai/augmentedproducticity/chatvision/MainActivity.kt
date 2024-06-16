package ai.augmentedproducticity.chatvision

import ai.augmentedproducticity.chatvision.ui.theme.ChatVisionTheme
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppContent()
        }
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

class MainViewModel : ViewModel() {
    private val client = OkHttpClient()
    var previewView: PreviewView? = null

    fun initializeCamera(context: ComponentActivity) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(context, cameraProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(context: ComponentActivity, cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView?.surfaceProvider)

        val imageCapture = ImageCapture.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (exc: Exception) {
            Log.e("Camera", "Use case binding failed", exc)
        }

        // Take Picture and process image for object detection
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                processImage(image)
                image.close()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("Camera", "Image capture failed", exception)
            }
        })
    }

    private fun processImage(image: ImageProxy) {
        viewModelScope.launch {
            val bitmap = image.toBitmap() // Convert image to bitmap
//            val detectedPoint = detectObject(bitmap)
            // TODO: Track object and provide feedback via vibration
        }
    }

    private suspend fun detectObject(bitmap: Bitmap): Point {
        val json = JSONObject()
        json.put("image", bitmap.toBase64())
        json.put("question", "Where is the object?")

        val requestBody = json.toString().toRequestBody()
        val request = Request.Builder()
            .url("https://api.openai.com/v1/images/detect") // Example URL
            .post(requestBody)
            .addHeader("Authorization", "Bearer YOUR_CHATGPT_API_KEY")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return Point(0, 0)
        val responseJson = JSONObject(responseBody)

        // Parse the response to get the detected point
        val pointJson = responseJson.getJSONObject("detected_point")
        return Point(pointJson.getInt("x"), pointJson.getInt("y"))
    }
}

// Extension functions to convert ImageProxy to Bitmap and Bitmap to Base64
fun ImageProxy.toBitmap(): Bitmap {
    // TODO: Convert ImageProxy to Bitmap
    // Placeholder code
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}

fun Bitmap.toBase64(): String {
    // TODO: Convert Bitmap to Base64 string

    // Placeholder code
    return ""
}
