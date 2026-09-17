package com.manhwaread.feature.settings

import com.manhwaread.core.common.AppError
import com.manhwaread.core.translation.ProviderDescriptor

// Состояние экрана настроек перевода. apiKeyInput — только то, что пользователь
// ввёл сейчас; сохранённый ключ в состояние НЕ загружается (безопасность).
data class SettingsUiState(
    val providers: List<ProviderDescriptor> = emptyList(),
    val providerId: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val apiKeyInput: String = "",
    val hasSavedApiKey: Boolean = false,
    val isBusy: Boolean = false,
    val message: SettingsMessage? = null,
) {
    val selectedProvider: ProviderDescriptor?
        get() = providers.firstOrNull { descriptor -> descriptor.id == providerId }

    val canSave: Boolean get() = providerId.isNotBlank() && !isBusy
}

// Одноразовые сообщения экрана (успех/ошибка сохранения и проверки).
sealed interface SettingsMessage {
    data object Saved : SettingsMessage
    data class TestSuccess(val providerDisplayName: String) : SettingsMessage
    data object ConfigurationInvalid : SettingsMessage
    data class ProviderError(val error: AppError) : SettingsMessage
}

// Колбеки экрана, сгруппированные против LongParameterList (приём ФАЗЫ 10).
data class SettingsActions(
    val onProviderSelected: (String) -> Unit,
    val onBaseUrlChange: (String) -> Unit,
    val onModelChange: (String) -> Unit,
    val onApiKeyChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onTestConnection: () -> Unit,
)
