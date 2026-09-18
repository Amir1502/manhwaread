package com.manhwaread.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manhwaread.core.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Корневой гейт приложения (ФАЗА 14): null — флаг онбординга ещё читается
 * из DataStore, false — показать онбординг, true — основные разделы.
 * Eagerly: чтение начинается до первой композиции, гейт не мигает.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    settingsStore: SettingsStore,
) : ViewModel() {
    val onboardingCompleted: StateFlow<Boolean?> = settingsStore.onboardingCompleted
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = null)
}
