package ai.augmentedproducticity.chatvision

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePreferences(context) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // API Key Section
            Text(
                text = "API Configuration",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Gemini API Key",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val maskedKey = securePrefs.getGeminiApiKey()?.let { key ->
                        if (key.length > 8) {
                            "${key.take(4)}...${key.takeLast(4)}"
                        } else {
                            "****"
                        }
                    } ?: "Not configured"

                    Text(
                        text = maskedKey,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showApiKeyDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Change Key")
                        }

                        OutlinedButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Key")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ChatVision",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AI-powered object detection and tracking using Google Gemini and OpenCV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Version 1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Change API Key Dialog
    if (showApiKeyDialog) {
        var newApiKey by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Change API Key") },
            text = {
                Column {
                    Text("Enter your new Gemini API key:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("AIza...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newApiKey.startsWith("AIza")) {
                            securePrefs.saveGeminiApiKey(newApiKey)
                            showApiKeyDialog = false
                        }
                    },
                    enabled = newApiKey.startsWith("AIza")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear API Key Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear API Key") },
            text = { Text("Are you sure you want to clear your API key? You'll need to re-enter it to use the app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        securePrefs.clearApiKeys()
                        showClearConfirmDialog = false
                        onBackPressed() // Go back since API key is now cleared
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
