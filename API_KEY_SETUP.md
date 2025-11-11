# API Key Setup Guide

## Overview

ChatVision now includes a secure, user-friendly API key management system. Users can enter their Gemini API key directly in the app on first launch, and it's stored securely using Android's EncryptedSharedPreferences.

## Features

### ✅ Secure Storage
- API keys are encrypted using AES256-GCM encryption
- Keys are stored in Android's EncryptedSharedPreferences
- Uses Android Keystore for key management
- Never stored in plain text

### ✅ First-Run Setup
- Clean, intuitive setup screen on first launch
- Shows information about getting a free Gemini API key
- Validates API key format (must start with "AIza")
- Debug builds auto-prefill from `local.properties` for development

### ✅ Settings Management
- Users can view their masked API key in settings
- Change API key at any time
- Clear API key option with confirmation
- Returns to setup screen when key is cleared

## Architecture

### New Files Created

```
app/src/main/java/ai/augmentedproducticity/chatvision/
├── SecurePreferences.kt          # Encrypted storage manager
├── ApiKeySetupScreen.kt          # First-run setup UI
└── SettingsScreen.kt             # Settings management UI
```

### Key Components

#### 1. **SecurePreferences.kt**
Handles all secure storage operations:
- `saveGeminiApiKey(apiKey: String)` - Encrypt and save API key
- `getGeminiApiKey(): String?` - Retrieve decrypted API key
- `isApiKeyConfigured(): Boolean` - Check if key exists
- `clearApiKeys()` - Remove stored API key
- `clearAll()` - Clear all secure preferences

#### 2. **ApiKeySetupScreen.kt**
First-run setup interface:
- Material 3 design with proper theming
- Password field with show/hide toggle
- Real-time validation
- Information about Gemini's free tier
- Link to ai.google.dev for getting API key

#### 3. **SettingsScreen.kt**
Settings management:
- View masked API key (e.g., "AIza...xyz123")
- Change API key with validation
- Clear key with confirmation dialog
- About section with app version

### Modified Files

#### **MainActivity.kt**
- Added conditional rendering: shows setup screen if no API key
- Auto-prefills API key in debug builds from BuildConfig
- Switches to main app after API key is configured

#### **MainViewModel.kt**
- Removed hardcoded `BuildConfig.GEMINI_API_KEY`
- Now retrieves API key from SecurePreferences
- Validates key exists before making API calls
- Proper error logging if key is missing

#### **build.gradle.kts**
- Added `androidx.security:security-crypto` dependency
- Fixed bug where geminiApiKey was checking chatGptApiKey
- Added default empty string for GEMINI_API_KEY in release builds

## Usage

### For End Users

#### First Time Setup
1. Launch the app
2. You'll see the "Setup ChatVision" screen
3. Enter your Gemini API key
4. Tap "Continue"
5. Start using the app!

#### Getting a Gemini API Key
1. Visit [ai.google.dev](https://ai.google.dev)
2. Sign in with Google account
3. Click "Get API Key" in Google AI Studio
4. Copy the API key (starts with "AIza")
5. Paste into ChatVision setup screen

#### Changing Your API Key
1. Open app settings (TODO: add settings button in main UI)
2. Tap "Change Key"
3. Enter new API key
4. Tap "Save"

#### Removing Your API Key
1. Open app settings
2. Tap "Clear Key"
3. Confirm the action
4. You'll be returned to the setup screen

### For Developers

#### Debug Mode Auto-Fill
In debug builds, the app automatically tries to prefill the API key from `BuildConfig.GEMINI_API_KEY` if available.

**Setup `local.properties`:**
```properties
GEMINI_API_KEY=AIzaSyC-YourActualAPIKeyHere
```

This makes development easier - the key will auto-fill in the setup screen, and you can just tap "Continue".

#### Testing Without API Key
If you want to test the setup flow:
1. Don't add `GEMINI_API_KEY` to `local.properties`
2. Or clear it: `SecurePreferences(context).clearApiKeys()`
3. Launch app - setup screen will appear

#### Accessing Stored API Key in Code
```kotlin
val securePrefs = SecurePreferences(context)
val apiKey = securePrefs.getGeminiApiKey()

if (apiKey.isNullOrBlank()) {
    // No API key configured
    // Show error or redirect to setup
} else {
    // Use the API key
    val model = GenerativeModel(
        modelName = "gemini-1.5-flash-001",
        apiKey = apiKey,
        generationConfig = generationConfig
    )
}
```

## Security Considerations

### ✅ What We Do Well
- **Encryption at Rest**: API keys encrypted with AES256-GCM
- **Android Keystore**: Master key stored in hardware-backed keystore
- **No Cloud Sync**: Keys never leave the device
- **Masked Display**: Keys shown as "AIza...xyz123" in UI
- **Validation**: Basic format validation (starts with "AIza")

### ⚠️ Limitations
- **Root/Jailbreak**: Encrypted data can be extracted on rooted devices
- **Backup**: Keys are not backed up (user must re-enter on new device)
- **Memory**: Keys briefly exist in memory as plain text during API calls
- **Logs**: Ensure no API keys are logged (check Log.d/Log.e calls)

### 🔒 Best Practices
- ✅ Always use `SecurePreferences` for API keys
- ✅ Never log API keys
- ✅ Don't pass keys through Intents
- ✅ Clear keys from memory when no longer needed
- ✅ Validate API key format before saving
- ❌ Don't store keys in regular SharedPreferences
- ❌ Don't hardcode keys in source code
- ❌ Don't transmit keys over unencrypted channels

## Migration from BuildConfig

### Before (Old Way)
```kotlin
val model = GenerativeModel(
    modelName = "gemini-1.5-flash-001",
    apiKey = BuildConfig.GEMINI_API_KEY,  // ❌ Hardcoded, requires rebuild
    generationConfig = generationConfig
)
```

### After (New Way)
```kotlin
val securePrefs = SecurePreferences(context)
val apiKey = securePrefs.getGeminiApiKey()

if (apiKey != null) {
    val model = GenerativeModel(
        modelName = "gemini-1.5-flash-001",
        apiKey = apiKey,  // ✅ Secure, user-configurable
        generationConfig = generationConfig
    )
}
```

### Benefits
- ✅ Users can enter their own API keys
- ✅ No need to rebuild app to change keys
- ✅ Keys stored securely, not in APK
- ✅ Better for distribution (Play Store, etc.)
- ✅ Supports multi-user scenarios
- ✅ Easier testing with different keys

## Troubleshooting

### API Key Not Saving
**Problem**: Setup screen keeps appearing after entering API key

**Solutions**:
1. Ensure API key starts with "AIza"
2. Check device storage permissions
3. Verify EncryptedSharedPreferences support (Android 6.0+)
4. Clear app data and try again

### Debug Prefill Not Working
**Problem**: API key doesn't auto-fill in debug builds

**Solutions**:
1. Check `local.properties` exists in project root
2. Verify `GEMINI_API_KEY=AIza...` is set correctly
3. Rebuild project after modifying `local.properties`
4. Check `BuildConfig.GEMINI_API_KEY` is not empty string

### "No API Key Configured" Error
**Problem**: App shows error even though key was entered

**Solutions**:
1. Open settings and verify key is shown (masked)
2. Try clearing and re-entering the key
3. Check `SecurePreferences.isApiKeyConfigured()` returns true
4. Verify no typos in key (common: lowercase L vs uppercase i)

## Future Enhancements

### Potential Improvements
- [ ] Add settings button to main UI
- [ ] Support for multiple API providers (OpenAI, Claude, etc.)
- [ ] API key validation via test request
- [ ] Usage tracking and quota warnings
- [ ] Backup/restore with encryption
- [ ] Biometric authentication for viewing keys
- [ ] In-app link to API key generation
- [ ] QR code scanning for API keys

## Testing Checklist

### Manual Testing
- [ ] First launch shows setup screen
- [ ] Valid API key saves and proceeds to main app
- [ ] Invalid API key shows error message
- [ ] Debug build auto-fills from local.properties
- [ ] Release build shows empty field
- [ ] Settings screen shows masked key
- [ ] Change key works and persists
- [ ] Clear key returns to setup screen
- [ ] API calls use stored key successfully
- [ ] App survives process death with key intact

### Edge Cases
- [ ] Empty API key rejected
- [ ] Very long API key handled
- [ ] Special characters in key handled
- [ ] App rotation preserves input
- [ ] Back press behavior correct
- [ ] Multiple rapid saves don't corrupt data

## Dependencies

### New Dependency Added
```gradle
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

**Purpose**: Provides `EncryptedSharedPreferences` for secure API key storage

**Alternatives Considered**:
- Android Keystore directly (too complex for simple string storage)
- Regular SharedPreferences (not secure enough)
- SQLCipher (overkill for single key-value pairs)

## Accessibility

### Features
- ✅ Screen reader compatible (all UI elements have descriptions)
- ✅ High contrast mode support
- ✅ Large text support
- ✅ Password field with show/hide toggle
- ✅ Clear error messages
- ✅ Keyboard navigation support

## Localization

Currently the UI is in English only. Future versions should support:
- Spanish (es)
- French (fr)
- German (de)
- Japanese (ja)
- Chinese (zh)

Strings are hardcoded currently - should be moved to `strings.xml` for i18n.

---

**Last Updated**: 2025-11-11
**Version**: 1.0
**Author**: ChatVision Team
