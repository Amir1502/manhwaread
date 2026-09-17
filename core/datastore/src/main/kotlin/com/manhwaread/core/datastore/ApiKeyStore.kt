package com.manhwaread.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Контракт хранилища API-ключей провайдеров перевода.
interface ApiKeyStore {
    fun saveApiKey(providerId: String, apiKey: String)
    fun apiKey(providerId: String): String?
    fun clearApiKey(providerId: String)
}

// Логика хранения поверх SharedPreferences; в проде файл шифрованный
// (createEncryptedPrefs), в тестах — обычные prefs (логика та же).
class SecureApiKeyStore(private val prefs: SharedPreferences) : ApiKeyStore {
    override fun saveApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString(keyFor(providerId), apiKey).apply()
    }

    override fun apiKey(providerId: String): String? = prefs.getString(keyFor(providerId), null)

    override fun clearApiKey(providerId: String) {
        prefs.edit().remove(keyFor(providerId)).apply()
    }

    private fun keyFor(providerId: String): String = "api_key_$providerId"
}

// Продакшен-файл: EncryptedSharedPreferences, мастер-ключ AES256_GCM живёт
// в Android Keystore. DoD: «ключи не в логах и не в открытом бэкапе».
// VERIFY-API: в security-crypto 1.1.0-alpha06 стабилен create-оверлоад
// (Context, fileName, MasterKey, PrefKeyEncryptionScheme, PrefValueEncryptionScheme).
fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

private const val SECURE_PREFS_FILE_NAME = "manhwaread_secure_prefs"
