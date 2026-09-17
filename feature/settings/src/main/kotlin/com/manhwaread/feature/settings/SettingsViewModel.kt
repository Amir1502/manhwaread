package com.manhwaread.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.datastore.ApiKeyStore
import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.datastore.TranslationSettings
import com.manhwaread.core.translation.ProviderConfig
import com.manhwaread.core.translation.TARGET_LANG_RU
import com.manhwaread.core.translation.TranslatableSegment
import com.manhwaread.core.translation.TranslationProviderFactory
import com.manhwaread.core.translation.TranslationRequest
import com.manhwaread.core.vision.DetectedLang
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val apiKeyStore: ApiKeyStore,
    private val providerFactory: TranslationProviderFactory,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState(providers = providerFactory.descriptors()))
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsStore.translationSettings.first()
            state.update { current ->
                current.copy(
                    providerId = saved.providerId,
                    baseUrl = saved.baseUrl,
                    model = saved.model,
                    hasSavedApiKey = hasKeyFor(saved.providerId),
                )
            }
        }
    }

    fun onProviderSelected(providerId: String) {
        state.update { current ->
            current.copy(
                providerId = providerId,
                apiKeyInput = "",
                hasSavedApiKey = hasKeyFor(providerId),
                message = null,
            )
        }
    }

    fun onBaseUrlChange(value: String) {
        state.update { current -> current.copy(baseUrl = value, message = null) }
    }

    fun onModelChange(value: String) {
        state.update { current -> current.copy(model = value, message = null) }
    }

    fun onApiKeyChange(value: String) {
        state.update { current -> current.copy(apiKeyInput = value, message = null) }
    }

    // Сохраняет настройки; введённый ключ уходит в шифрованное хранилище.
    fun save() {
        viewModelScope.launch {
            val current = state.value
            settingsStore.updateTranslation(
                TranslationSettings(
                    providerId = current.providerId,
                    baseUrl = current.baseUrl.trim(),
                    model = current.model.trim(),
                    targetLang = TARGET_LANG_RU,
                ),
            )
            if (current.apiKeyInput.isNotBlank()) {
                apiKeyStore.saveApiKey(current.providerId, current.apiKeyInput.trim())
            }
            state.update { updated ->
                updated.copy(
                    apiKeyInput = "",
                    hasSavedApiKey = hasKeyFor(updated.providerId),
                    message = SettingsMessage.Saved,
                )
            }
        }
    }

    // Проверка соединения: минимальный запрос через выбранного провайдера.
    fun testConnection() {
        viewModelScope.launch {
            state.update { current -> current.copy(isBusy = true, message = null) }
            val current = state.value
            val apiKey = current.apiKeyInput.ifBlank { apiKeyStore.apiKey(current.providerId).orEmpty() }
            val provider = providerFactory.create(
                ProviderConfig(
                    id = current.providerId,
                    apiKey = apiKey,
                    baseUrl = current.baseUrl.trim().ifBlank { null },
                    model = current.model.trim().ifBlank { null },
                ),
            )
            if (provider == null) {
                state.update { updated -> updated.copy(isBusy = false, message = SettingsMessage.ConfigurationInvalid) }
                return@launch
            }
            val probe = provider.translate(probeRequest())
            val response = probe.getOrNull()
            val message = if (response != null) {
                SettingsMessage.TestSuccess(provider.displayName)
            } else {
                SettingsMessage.ProviderError((probe as DomainResult.Failure).error)
            }
            state.update { updated -> updated.copy(isBusy = false, message = message) }
        }
    }

    private fun hasKeyFor(providerId: String): Boolean =
        providerId.isNotBlank() && !apiKeyStore.apiKey(providerId).isNullOrBlank()

    // Один короткий сегмент: проверка дешёвая для любого провайдера.
    private fun probeRequest() = TranslationRequest(
        segments = listOf(TranslatableSegment(id = PROBE_ID, text = PROBE_TEXT)),
        sourceLang = DetectedLang.EN,
        targetLang = TARGET_LANG_RU,
    )

    private companion object {
        const val PROBE_ID = "probe-1"
        const val PROBE_TEXT = "Hello"
    }
}
