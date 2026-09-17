package com.manhwaread.core.translation

import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationProviderFactoryTest {
    private val factory = TranslationProviderFactory(OkHttpClient())

    private fun config(id: String, apiKey: String = "key", baseUrl: String? = "https://api.example.com/v1") =
        ProviderConfig(id, apiKey, baseUrl)

    @Test
    fun `openai-compat with key and baseUrl creates provider`() {
        assertTrue(factory.create(config(ProviderIds.OPENAI_COMPAT)) is OpenAiCompatProvider)
    }

    @Test
    fun `openai-compat without baseUrl is rejected`() {
        assertNull(factory.create(config(ProviderIds.OPENAI_COMPAT, baseUrl = null)))
        assertNull(factory.create(config(ProviderIds.OPENAI_COMPAT, baseUrl = " ")))
    }

    @Test
    fun `gemini and deepl need only api key`() {
        assertTrue(factory.create(config(ProviderIds.GEMINI, baseUrl = null)) is GeminiProvider)
        assertTrue(factory.create(config(ProviderIds.DEEPL, baseUrl = null)) is DeepLProvider)
    }

    @Test
    fun `blank api key is rejected for every provider`() {
        assertNull(factory.create(config(ProviderIds.OPENAI_COMPAT, apiKey = "")))
        assertNull(factory.create(config(ProviderIds.GEMINI, apiKey = "")))
        assertNull(factory.create(config(ProviderIds.DEEPL, apiKey = "")))
    }

    @Test
    fun `unknown provider id is rejected`() {
        assertNull(factory.create(config("yandex")))
    }

    @Test
    fun `descriptors cover all providers with correct flags`() {
        val descriptors = factory.descriptors().associateBy { descriptor -> descriptor.id }
        assertEquals(setOf(ProviderIds.OPENAI_COMPAT, ProviderIds.GEMINI, ProviderIds.DEEPL), descriptors.keys)
        assertTrue(descriptors.getValue(ProviderIds.OPENAI_COMPAT).requiresBaseUrl)
        assertEquals(OpenAiCompatProvider.DEFAULT_MODEL, descriptors.getValue(ProviderIds.OPENAI_COMPAT).defaultModel)
        assertNull(descriptors.getValue(ProviderIds.DEEPL).defaultModel)
    }
}
