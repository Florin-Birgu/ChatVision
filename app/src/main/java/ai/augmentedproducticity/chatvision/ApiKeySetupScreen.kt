package ai.augmentedproducticity.chatvision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySetupScreen(
    initialApiKey: String = "",
    onApiKeyConfigured: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup ChatVision") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App title
            Text(
                text = "Welcome to ChatVision",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = "Find objects using AI-powered vision",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Google Gemini API Key Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This app uses Google Gemini AI for object detection. " +
                                "The free tier provides 1,500 requests per day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Get your free API key at: ai.google.dev",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // API Key input field
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    errorMessage = null
                },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIza...") },
                supportingText = {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Your API key is stored securely and never shared")
                    }
                },
                isError = errorMessage != null,
                visualTransformation = if (isApiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                        Icon(
                            imageVector = if (isApiKeyVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = if (isApiKeyVisible) {
                                "Hide API key"
                            } else {
                                "Show API key"
                            }
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (validateAndSaveApiKey(apiKey, context)) {
                            onApiKeyConfigured(apiKey)
                        } else {
                            errorMessage = "Please enter a valid API key (starts with 'AIza')"
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button
            Button(
                onClick = {
                    if (validateAndSaveApiKey(apiKey, context)) {
                        onApiKeyConfigured(apiKey)
                    } else {
                        errorMessage = "Please enter a valid API key (starts with 'AIza')"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = apiKey.isNotBlank()
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Help text
            Text(
                text = "Don't have an API key? Visit ai.google.dev to get started for free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Validate and save API key
 */
private fun validateAndSaveApiKey(apiKey: String, context: android.content.Context): Boolean {
    // Basic validation: Gemini API keys start with "AIza"
    if (apiKey.isBlank() || !apiKey.startsWith("AIza")) {
        return false
    }

    // Save to secure storage
    val securePrefs = SecurePreferences(context)
    securePrefs.saveGeminiApiKey(apiKey)

    return true
}
