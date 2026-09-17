package com.manhwaread.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

// Логика SecureApiKeyStore проверяется на обычных SharedPreferences:
// поведение одинаково для шифрованного и простого файла, а создание
// EncryptedSharedPreferences (createEncryptedPrefs) — платформенный клей
// на Android Keystore, вне досягаемости unit-тестов.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SecureApiKeyStoreTest {
    private fun newStore(): SecureApiKeyStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("keys-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        return SecureApiKeyStore(prefs)
    }

    @Test
    fun `saved key is readable`() {
        val store = newStore()
        store.saveApiKey("gemini", "secret-key")
        assertEquals("secret-key", store.apiKey("gemini"))
    }

    @Test
    fun `missing key returns null`() {
        assertNull(newStore().apiKey("deepl"))
    }

    @Test
    fun `clear removes key`() {
        val store = newStore()
        store.saveApiKey("gemini", "secret-key")
        store.clearApiKey("gemini")
        assertNull(store.apiKey("gemini"))
    }

    @Test
    fun `providers are isolated and overwrite works`() {
        val store = newStore()
        store.saveApiKey("gemini", "key-a")
        store.saveApiKey("deepl", "key-b")
        store.saveApiKey("gemini", "key-a2")
        assertEquals("key-a2", store.apiKey("gemini"))
        assertEquals("key-b", store.apiKey("deepl"))
    }
}
