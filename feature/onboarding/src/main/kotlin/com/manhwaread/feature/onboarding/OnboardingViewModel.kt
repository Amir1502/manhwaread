package com.manhwaread.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel онбординга (ФАЗА 14): три страницы, завершение пишет флаг в DataStore —
// повторно онбординг не показывается (гейт в корневом экране приложения).
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNextPage() {
        _uiState.update { state -> state.copy(page = (state.page + 1).coerceAtMost(LAST_PAGE)) }
    }

    fun onPreviousPage() {
        _uiState.update { state -> state.copy(page = (state.page - 1).coerceAtLeast(FIRST_PAGE)) }
    }

    fun onPageChanged(page: Int) {
        _uiState.update { state -> state.copy(page = page.coerceIn(FIRST_PAGE, LAST_PAGE)) }
    }

    fun onFinish() {
        viewModelScope.launch {
            settingsStore.setOnboardingCompleted(true)
        }
    }

    companion object {
        const val PAGE_COUNT = 3
        const val FIRST_PAGE = 0
        const val LAST_PAGE = PAGE_COUNT - 1
    }
}
