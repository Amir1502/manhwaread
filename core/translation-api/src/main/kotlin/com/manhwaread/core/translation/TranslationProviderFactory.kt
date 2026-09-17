package com.manhwaread.core.translation

import okhttp3.OkHttpClient

// Описание провайдера для экрана настроек: что требует конфигурации.
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val requiresBaseUrl: Boolean,
    val defaultModel: String?,
)

// Фабрика провайдеров: настройки пользователя → готовый TranslationProvider.
// Невалидная конфигурация (пустой ключ, нет baseUrl где он обязателен) → null,
// экран настроек показывает причину вместо падения конвейера.
class TranslationProviderFactory(private val httpClient: OkHttpClient) {
    fun create(config: ProviderConfig): TranslationProvider? =
        when (config.id) {
            ProviderIds.OPENAI_COMPAT ->
                if (config.apiKey.isBlank() || config.baseUrl.isNullOrBlank()) {
                    null
                } else {
                    OpenAiCompatProvider(config, httpClient)
                }
            ProviderIds.GEMINI ->
                if (config.apiKey.isBlank()) null else GeminiProvider(config, httpClient)
            ProviderIds.DEEPL ->
                if (config.apiKey.isBlank()) null else DeepLProvider(config, httpClient)
            else -> null
        }

    fun descriptors(): List<ProviderDescriptor> =
        listOf(
            ProviderDescriptor(
                id = ProviderIds.OPENAI_COMPAT,
                displayName = "OpenAI-compatible",
                requiresBaseUrl = true,
                defaultModel = OpenAiCompatProvider.DEFAULT_MODEL,
            ),
            ProviderDescriptor(
                id = ProviderIds.GEMINI,
                displayName = "Google Gemini",
                requiresBaseUrl = false,
                defaultModel = GeminiProvider.DEFAULT_MODEL,
            ),
            ProviderDescriptor(
                id = ProviderIds.DEEPL,
                displayName = "DeepL",
                requiresBaseUrl = false,
                defaultModel = null,
            ),
        )
}
