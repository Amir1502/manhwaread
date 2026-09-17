package com.manhwaread.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.manhwaread.core.translation.TARGET_LANG_RU
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Настройки перевода: что выбрано в экране настроек. API-ключ здесь НЕ
// хранится — он живёт в ApiKeyStore (EncryptedSharedPreferences).
data class TranslationSettings(
    val providerId: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val targetLang: String = TARGET_LANG_RU,
) {
    // Провайдер выбран — остальное проверяет фабрика провайдеров.
    val isProviderSelected: Boolean get() = providerId.isNotBlank()
}

// Контракт хранилища настроек (интерфейс — для подмены в тестах ViewModel).
interface SettingsStore {
    val translationSettings: Flow<TranslationSettings>
    suspend fun updateTranslation(settings: TranslationSettings)
}

// Реализация на Preferences DataStore: переживает перезапуск, не в логах.
class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val translationSettings: Flow<TranslationSettings> =
        dataStore.data.map { prefs ->
            TranslationSettings(
                providerId = prefs[KEY_PROVIDER_ID].orEmpty(),
                baseUrl = prefs[KEY_BASE_URL].orEmpty(),
                model = prefs[KEY_MODEL].orEmpty(),
                targetLang = prefs[KEY_TARGET_LANG] ?: TARGET_LANG_RU,
            )
        }

    override suspend fun updateTranslation(settings: TranslationSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ID] = settings.providerId
            prefs[KEY_BASE_URL] = settings.baseUrl
            prefs[KEY_MODEL] = settings.model
            prefs[KEY_TARGET_LANG] = settings.targetLang
        }
    }

    companion object {
        private val KEY_PROVIDER_ID = stringPreferencesKey("translation_provider_id")
        private val KEY_BASE_URL = stringPreferencesKey("translation_base_url")
        private val KEY_MODEL = stringPreferencesKey("translation_model")
        private val KEY_TARGET_LANG = stringPreferencesKey("translation_target_lang")
    }
}
