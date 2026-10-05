package com.manhwaread.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Настройки читалки по тайтлу (Этап 8): режим чтения запоминается отдельно
// для каждого mangaId. Режим хранится строкой (имя enum читалки) — :core
// не зависит от :feature:reader, а разбор имени выполняет хост (:app).
interface ReaderSettingsStore {
    /** Сохранённое имя режима читалки для тайтла; null — режим ещё не выбирался. */
    fun readerMode(mangaId: Long): Flow<String?>

    suspend fun setReaderMode(mangaId: Long, mode: String)
}

// Реализация на общем Preferences DataStore приложения: ключ reader_mode_{mangaId}.
class DataStoreReaderSettingsStore(private val dataStore: DataStore<Preferences>) : ReaderSettingsStore {
    override fun readerMode(mangaId: Long): Flow<String?> =
        dataStore.data
            .map { prefs -> prefs[readerModeKey(mangaId)] }
            .distinctUntilChanged()

    override suspend fun setReaderMode(mangaId: Long, mode: String) {
        dataStore.edit { prefs -> prefs[readerModeKey(mangaId)] = mode }
    }

    private fun readerModeKey(mangaId: Long): Preferences.Key<String> =
        stringPreferencesKey("$READER_MODE_KEY_PREFIX$mangaId")

    private companion object {
        const val READER_MODE_KEY_PREFIX = "reader_mode_"
    }
}
