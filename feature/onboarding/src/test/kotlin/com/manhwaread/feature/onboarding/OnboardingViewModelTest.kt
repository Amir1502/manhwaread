package com.manhwaread.feature.onboarding

import com.manhwaread.core.datastore.SettingsStore
import com.manhwaread.core.datastore.TranslationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @BeforeEach
    fun setMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeSettingsStore : SettingsStore {
        private val translation = MutableStateFlow(TranslationSettings())
        private val onboarding = MutableStateFlow(false)

        override val translationSettings: Flow<TranslationSettings> = translation

        override suspend fun updateTranslation(settings: TranslationSettings) {
            translation.value = settings
        }

        override val onboardingCompleted: Flow<Boolean> = onboarding

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            onboarding.value = completed
        }

        fun currentOnboardingCompleted(): Boolean = onboarding.value
    }

    private val settingsStore = FakeSettingsStore()

    private fun viewModel(): OnboardingViewModel = OnboardingViewModel(settingsStore)

    @Test
    fun `initial page is first`() {
        assertEquals(OnboardingViewModel.FIRST_PAGE, viewModel().uiState.value.page)
    }

    @Test
    fun `next page advances and clamps at last`() {
        val viewModel = viewModel()
        viewModel.onNextPage()
        assertEquals(1, viewModel.uiState.value.page)
        viewModel.onNextPage()
        assertEquals(OnboardingViewModel.LAST_PAGE, viewModel.uiState.value.page)
        viewModel.onNextPage()
        assertEquals(OnboardingViewModel.LAST_PAGE, viewModel.uiState.value.page)
    }

    @Test
    fun `previous page retreats and clamps at first`() {
        val viewModel = viewModel()
        viewModel.onNextPage()
        viewModel.onPreviousPage()
        assertEquals(OnboardingViewModel.FIRST_PAGE, viewModel.uiState.value.page)
        viewModel.onPreviousPage()
        assertEquals(OnboardingViewModel.FIRST_PAGE, viewModel.uiState.value.page)
    }

    @Test
    fun `pager swipe updates page with coercion`() {
        val viewModel = viewModel()
        viewModel.onPageChanged(2)
        assertEquals(2, viewModel.uiState.value.page)
        viewModel.onPageChanged(99)
        assertEquals(OnboardingViewModel.LAST_PAGE, viewModel.uiState.value.page)
        viewModel.onPageChanged(-5)
        assertEquals(OnboardingViewModel.FIRST_PAGE, viewModel.uiState.value.page)
    }

    @Test
    fun `finish marks onboarding completed in store`() = runTest {
        assertFalse(settingsStore.currentOnboardingCompleted())
        val viewModel = viewModel()
        viewModel.onFinish()
        advanceUntilIdle()
        assertTrue(settingsStore.currentOnboardingCompleted())
    }
}
