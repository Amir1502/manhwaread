package com.manhwaread.feature.onboarding

data class OnboardingUiState(
    val page: Int = 0,
)

// Группировка колбэков экрана (избегает detekt LongParameterList).
data class OnboardingActions(
    val onNextPage: () -> Unit,
    val onPreviousPage: () -> Unit,
    val onPageChanged: (Int) -> Unit,
    val onFinish: () -> Unit,
)
