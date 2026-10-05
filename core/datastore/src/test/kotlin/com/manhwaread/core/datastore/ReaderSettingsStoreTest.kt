package com.manhwaread.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

// Режим читалки по тайтлу поверх реального Preferences DataStore (временный файл Robolectric).
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReaderSettingsStoreTest {
    private fun newStore(): DataStoreReaderSettingsStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "reader-${UUID.randomUUID()}.preferences_pb")
        return DataStoreReaderSettingsStore(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun `mode is null until chosen`() = runBlocking {
        assertNull(newStore().readerMode(mangaId = 1L).first())
    }

    @Test
    fun `mode roundtrips per manga and titles do not interfere`() = runBlocking {
        val store = newStore()
        store.setReaderMode(mangaId = 1L, mode = "RTL")
        store.setReaderMode(mangaId = 2L, mode = "WEBTOON")

        assertEquals("RTL", store.readerMode(1L).first())
        assertEquals("WEBTOON", store.readerMode(2L).first())
        assertNull(store.readerMode(3L).first())
    }

    @Test
    fun `collector receives only changes of its own title`() = runBlocking {
        val store = newStore()
        store.readerMode(1L).test {
            assertNull(awaitItem())
            // Запись другого тайтла не порождает эмиссию (distinctUntilChanged).
            store.setReaderMode(mangaId = 2L, mode = "LTR")
            store.setReaderMode(mangaId = 1L, mode = "VERTICAL")
            assertEquals("VERTICAL", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
