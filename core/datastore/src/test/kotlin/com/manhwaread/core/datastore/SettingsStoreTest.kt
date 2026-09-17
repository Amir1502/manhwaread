package com.manhwaread.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

// Реальный Preferences DataStore поверх временного файла Robolectric.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {
    private fun newStore(): DataStoreSettingsStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        return DataStoreSettingsStore(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun `defaults are empty settings`() = runBlocking {
        val settings = newStore().translationSettings.first()
        assertEquals(TranslationSettings(), settings)
    }

    @Test
    fun `update roundtrips through datastore`() = runBlocking {
        val store = newStore()
        val updated = TranslationSettings(
            providerId = "gemini",
            baseUrl = "https://proxy.example.com",
            model = "gemini-1.5-flash",
            targetLang = "ru",
        )
        store.updateTranslation(updated)
        assertEquals(updated, store.translationSettings.first())
    }

    @Test
    fun `active collector receives update`() = runBlocking {
        val store = newStore()
        val updated = TranslationSettings(providerId = "deepl")
        store.translationSettings.test {
            // Первое излучение — дефолт из пустого файла.
            assertEquals(TranslationSettings(), awaitItem())
            store.updateTranslation(updated)
            assertEquals(updated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
