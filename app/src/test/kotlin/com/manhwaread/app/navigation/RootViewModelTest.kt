package com.manhwaread.app.navigation

import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.datastore.TranslationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeSettingsStore(
        private val onboardingSource: Flow<Boolean>,
    ) : SettingsStore {
        private val translation = MutableStateFlow(TranslationSettings())

        override val translationSettings: Flow<TranslationSettings> = translation

        override suspend fun updateTranslation(settings: TranslationSettings) {
            translation.value = settings
        }

        override val onboardingCompleted: Flow<Boolean> = onboardingSource

        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
    }

    @Test
    fun `flag is null while store value not yet read`() = runTest {
        val viewModel = RootViewModel(FakeSettingsStore(emptyFlow()))
        advanceUntilIdle()
        assertNull(viewModel.onboardingCompleted.value)
    }

    @Test
    fun `flag is false when onboarding not completed`() = runTest {
        val viewModel = RootViewModel(FakeSettingsStore(MutableStateFlow(false)))
        advanceUntilIdle()
        assertEquals(false, viewModel.onboardingCompleted.value)
    }

    @Test
    fun `flag follows store updates`() = runTest {
        val source = MutableStateFlow(false)
        val viewModel = RootViewModel(FakeSettingsStore(source))
        advanceUntilIdle()
        assertEquals(false, viewModel.onboardingCompleted.value)
        source.value = true
        advanceUntilIdle()
        assertEquals(true, viewModel.onboardingCompleted.value)
    }
}
