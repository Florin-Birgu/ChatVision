package ai.augmentedproducticity.chatvision

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for API keys and sensitive data using Android's EncryptedSharedPreferences
 */
class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val PREFS_NAME = "chatvision_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_API_KEY_CONFIGURED = "api_key_configured"
    }

    /**
     * Save Gemini API key securely
     */
    fun saveGeminiApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_GEMINI_API_KEY, apiKey)
            .putBoolean(KEY_API_KEY_CONFIGURED, true)
            .apply()
    }

    /**
     * Retrieve Gemini API key
     */
    fun getGeminiApiKey(): String? {
        return sharedPreferences.getString(KEY_GEMINI_API_KEY, null)
    }

    /**
     * Check if API key has been configured
     */
    fun isApiKeyConfigured(): Boolean {
        return sharedPreferences.getBoolean(KEY_API_KEY_CONFIGURED, false) &&
                !getGeminiApiKey().isNullOrBlank()
    }

    /**
     * Clear all stored API keys
     */
    fun clearApiKeys() {
        sharedPreferences.edit()
            .remove(KEY_GEMINI_API_KEY)
            .putBoolean(KEY_API_KEY_CONFIGURED, false)
            .apply()
    }

    /**
     * Clear all secure preferences
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
