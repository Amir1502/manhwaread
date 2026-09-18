package com.manhwaread.feature.settings

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.datastore.ApiKeyStore
import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.datastore.TranslationSettings
import com.manhwaread.core.translation.ProviderConfig
import com.manhwaread.core.translation.ProviderDescriptor
import com.manhwaread.core.translation.TranslationProvider
import com.manhwaread.core.translation.TranslationProviderFactory
import com.manhwaread.core.translation.TranslationRequest
import com.manhwaread.core.translation.TranslationResponse
import com.manhwaread.core.vision.DetectedLang
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeEach
    fun setUpMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeSettingsStore(initial: TranslationSettings = TranslationSettings()) : SettingsStore {
        private val current = MutableStateFlow(initial)
        private val onboarding = MutableStateFlow(false)
        var saved: TranslationSettings? = null
        var onboardingSaved: Boolean? = null
        override val translationSettings: Flow<TranslationSettings> = current.asStateFlow()

        override suspend fun updateTranslation(settings: TranslationSettings) {
            saved = settings
            current.value = settings
        }

        override val onboardingCompleted: Flow<Boolean> = onboarding.asStateFlow()

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            onboardingSaved = completed
            onboarding.value = completed
        }
    }

    private class FakeApiKeyStore : ApiKeyStore {
        val keys = mutableMapOf<String, String>()
        override fun saveApiKey(providerId: String, apiKey: String) {
            keys[providerId] = apiKey
        }

        override fun apiKey(providerId: String): String? = keys[providerId]

        override fun clearApiKey(providerId: String) {
            keys.remove(providerId)
        }
    }

    private fun fakeProvider(result: DomainResult<TranslationResponse>): TranslationProvider =
        object : TranslationProvider {
            override val id: String = "test-provider"
            override val displayName: String = "Test Provider"
            override fun supports(sourceLang: DetectedLang, targetLang: String): Boolean = true
            override suspend fun translate(request: TranslationRequest): DomainResult<TranslationResponse> = result
        }

    private val descriptors = listOf(
        ProviderDescriptor("gemini", "Google Gemini", requiresBaseUrl = false, defaultModel = "gemini-1.5-flash"),
        ProviderDescriptor("deepl", "DeepL", requiresBaseUrl = false, defaultModel = null),
    )

    private fun mockFactory(provider: TranslationProvider?, configSlot: CapturingSlot<ProviderConfig>? = null) =
        mockk<TranslationProviderFactory>().apply {
            every { descriptors() } returns this@SettingsViewModelTest.descriptors
            if (configSlot != null) {
                every { create(capture(configSlot)) } returns provider
            } else {
                every { create(any()) } returns provider
            }
        }

    private val successProvider = fakeProvider(DomainResult.success(TranslationResponse("[]")))

    @Test
    fun `init loads saved settings and key flag`() = runTest {
        val store = FakeSettingsStore(TranslationSettings(providerId = "gemini", model = "m1"))
        val keys = FakeApiKeyStore()
        keys.saveApiKey("gemini", "stored")
        val viewModel = SettingsViewModel(store, keys, mockFactory(successProvider))
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("gemini", state.providerId)
        assertEquals("m1", state.model)
        assertTrue(state.hasSavedApiKey)
        assertEquals(descriptors, state.providers)
    }

    @Test
    fun `provider selection resets key input and message`() = runTest {
        val keys = FakeApiKeyStore()
        keys.saveApiKey("deepl", "d-key")
        val viewModel = SettingsViewModel(FakeSettingsStore(), keys, mockFactory(successProvider))
        advanceUntilIdle()
        viewModel.onApiKeyChange("draft")
        viewModel.onProviderSelected("deepl")
        val state = viewModel.uiState.value
        assertEquals("deepl", state.providerId)
        assertEquals("", state.apiKeyInput)
        assertTrue(state.hasSavedApiKey)
        assertNull(state.message)
    }

    @Test
    fun `save persists settings and new key`() = runTest {
        val store = FakeSettingsStore()
        val keys = FakeApiKeyStore()
        val viewModel = SettingsViewModel(store, keys, mockFactory(successProvider))
        advanceUntilIdle()
        viewModel.onProviderSelected("gemini")
        viewModel.onBaseUrlChange(" https://proxy ")
        viewModel.onModelChange(" gemini-pro ")
        viewModel.onApiKeyChange(" new-key ")
        viewModel.save()
        advanceUntilIdle()
        assertEquals(
            TranslationSettings("gemini", "https://proxy", "gemini-pro", "ru"),
            store.saved,
        )
        assertEquals("new-key", keys.apiKey("gemini"))
        val state = viewModel.uiState.value
        assertEquals("", state.apiKeyInput)
        assertTrue(state.hasSavedApiKey)
        assertEquals(SettingsMessage.Saved, state.message)
    }

    @Test
    fun `save with blank input keeps stored key`() = runTest {
        val store = FakeSettingsStore()
        val keys = FakeApiKeyStore()
        keys.saveApiKey("gemini", "old-key")
        val viewModel = SettingsViewModel(store, keys, mockFactory(successProvider))
        advanceUntilIdle()
        viewModel.onProviderSelected("gemini")
        viewModel.save()
        advanceUntilIdle()
        assertEquals("old-key", keys.apiKey("gemini"))
        assertTrue(viewModel.uiState.value.hasSavedApiKey)
    }

    @Test
    fun `test connection success reports provider name`() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsStore(), FakeApiKeyStore(), mockFactory(successProvider))
        advanceUntilIdle()
        viewModel.onProviderSelected("gemini")
        viewModel.onApiKeyChange("k")
        viewModel.testConnection()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SettingsMessage.TestSuccess("Test Provider"), state.message)
        assertEquals(false, state.isBusy)
    }

    @Test
    fun `test connection failure maps provider error`() = runTest {
        val failing = fakeProvider(DomainResult.failure(AppError.ProviderAuth))
        val viewModel = SettingsViewModel(FakeSettingsStore(), FakeApiKeyStore(), mockFactory(failing))
        advanceUntilIdle()
        viewModel.onProviderSelected("gemini")
        viewModel.onApiKeyChange("bad")
        viewModel.testConnection()
        advanceUntilIdle()
        assertEquals(
            SettingsMessage.ProviderError(AppError.ProviderAuth),
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun `test connection with invalid config reports ConfigurationInvalid`() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsStore(), FakeApiKeyStore(), mockFactory(null))
        advanceUntilIdle()
        viewModel.onProviderSelected("gemini")
        viewModel.testConnection()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(SettingsMessage.ConfigurationInvalid, state.message)
        assertEquals(false, state.isBusy)
    }

    @Test
    fun `test connection uses stored key when input blank`() = runTest {
        val keys = FakeApiKeyStore()
        keys.saveApiKey("gemini", "stored-key")
        val configSlot = slot<ProviderConfig>()
        val viewModel = SettingsViewModel(FakeSettingsStore(), keys, mockFactory(successProvider, configSlot))
        advanceUntilIdle()
        viewModel.onProviderSelected("gemini")
        viewModel.testConnection()
        advanceUntilIdle()
        assertEquals("gemini", configSlot.captured.id)
        assertEquals("stored-key", configSlot.captured.apiKey)
        // Пустой baseUrl в конфигурации провайдера — null, не пустая строка.
        assertNull(configSlot.captured.baseUrl)
    }
}
