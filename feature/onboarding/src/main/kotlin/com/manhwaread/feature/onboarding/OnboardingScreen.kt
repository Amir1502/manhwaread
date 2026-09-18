package com.manhwaread.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val ContentPadding = 24.dp
private val IndicatorSize = 8.dp
private val IndicatorSpacing = 6.dp
private val IconSize = 72.dp

// Точка входа онбординга: показывается корневым экраном до основного UI.
@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        actions = OnboardingActions(
            onNextPage = viewModel::onNextPage,
            onPreviousPage = viewModel::onPreviousPage,
            onPageChanged = viewModel::onPageChanged,
            onFinish = viewModel::onFinish,
        ),
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = state.page) { OnboardingViewModel.PAGE_COUNT }
    // Кнопки управляют страницей через ViewModel; свайп сообщает обратно.
    LaunchedEffect(state.page) {
        if (pagerState.currentPage != state.page) {
            pagerState.animateScrollToPage(state.page)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> actions.onPageChanged(page) }
    }
    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            OnboardingPage(page = page)
        }
        PageIndicator(currentPage = state.page)
        Row(
            modifier = Modifier.fillMaxWidth().padding(ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(IndicatorSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.page > OnboardingViewModel.FIRST_PAGE) {
                TextButton(onClick = actions.onPreviousPage) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            TextButton(onClick = actions.onFinish, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_skip))
            }
            Button(onClick = if (state.page == OnboardingViewModel.LAST_PAGE) actions.onFinish else actions.onNextPage) {
                Text(
                    stringResource(
                        if (state.page == OnboardingViewModel.LAST_PAGE) {
                            R.string.onboarding_start
                        } else {
                            R.string.onboarding_next
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    val icon: ImageVector
    val titleRes: Int
    val bodyRes: Int
    when (page) {
        1 -> {
            icon = Icons.Filled.Layers
            titleRes = R.string.onboarding_overlay_title
            bodyRes = R.string.onboarding_overlay_body
        }
        2 -> {
            icon = Icons.Filled.Translate
            titleRes = R.string.onboarding_providers_title
            bodyRes = R.string.onboarding_providers_body
        }
        else -> {
            icon = Icons.Filled.AutoAwesome
            titleRes = R.string.onboarding_welcome_title
            bodyRes = R.string.onboarding_welcome_body
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize),
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = ContentPadding),
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = IndicatorSpacing),
        )
    }
}

@Composable
private fun PageIndicator(currentPage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(IndicatorSpacing, Alignment.CenterHorizontally),
    ) {
        repeat(OnboardingViewModel.PAGE_COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(IndicatorSize)
                    .background(
                        color = if (index == currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
