package com.manhwaread.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manhwaread.core.designsystem.ManhwareadPalette
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.ManhwareadTypography

private val BarHorizontalPadding = 16.dp
private val BarVerticalPadding = 8.dp
private val TopBarCornerRadius = 16.dp
private val BottomBarCornerRadius = 16.dp
private val SliderHorizontalPadding = 8.dp
private val InfoStripVerticalPadding = 4.dp
private val InfoStripHorizontalPadding = 12.dp
private val InfoStripBottomOffset = 16.dp
private val InfoStripRadius = 12.dp
private const val BAR_SURFACE_ALPHA = 0.94f
private const val INFO_STRIP_ALPHA = 0.72f
private const val ACTIVE_OVERLAY_BG_ALPHA = 0.20f
private const val INACTIVE_OVERLAY_BG_ALPHA = 0.12f

@Composable
fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ManhwareadPalette.DarkSurface.copy(alpha = BAR_SURFACE_ALPHA),
        shape = RoundedCornerShape(bottomStart = TopBarCornerRadius, bottomEnd = TopBarCornerRadius),
        tonalElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = ManhwareadPalette.DarkOnSurface,
                )
            }
            Text(
                text = title,
                style = ManhwareadTypography.titleMedium,
                color = ManhwareadPalette.DarkOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = BarHorizontalPadding),
            )
        }
    }
}

@Composable
fun ReaderBottomBar(
    state: ReaderUiState,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onPageChangeFinished: () -> Unit,
    onToggleOverlay: () -> Unit,
    onSelectMode: (ReaderMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ManhwareadPalette.DarkSurface.copy(alpha = BAR_SURFACE_ALPHA),
        shape = RoundedCornerShape(topStart = BottomBarCornerRadius, topEnd = BottomBarCornerRadius),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomInset)
                .padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
        ) {
            ReaderSliderRow(
                currentPage = state.currentPageIndex,
                pageCount = pageCount,
                onPageChange = onPageChange,
                onPageChangeFinished = onPageChangeFinished,
            )
            ReaderControlsRow(
                state = state,
                onToggleOverlay = onToggleOverlay,
                onSelectMode = onSelectMode,
            )
        }
    }
}

@Composable
private fun ReaderSliderRow(
    currentPage: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onPageChangeFinished: () -> Unit,
) {
    val totalPages = pageCount.coerceAtLeast(1)
    val displayPage = (currentPage + 1).coerceIn(1, totalPages)
    val sliderDesc = stringResource(R.string.reader_slider_description)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BarVerticalPadding),
    ) {
        if (pageCount > 1) {
            val maxSliderIndex = (pageCount - 1).toFloat()
            val sliderValue = currentPage.toFloat().coerceIn(0f, maxSliderIndex)
            val sliderSteps = if (pageCount > 2) pageCount - 2 else 0

            Slider(
                value = sliderValue,
                onValueChange = { value -> onPageChange(value.toInt()) },
                onValueChangeFinished = onPageChangeFinished,
                valueRange = 0f..maxSliderIndex,
                steps = sliderSteps,
                colors = SliderDefaults.colors(
                    thumbColor = ManhwareadPalette.DarkPrimary,
                    activeTrackColor = ManhwareadPalette.DarkPrimary,
                    inactiveTrackColor = ManhwareadPalette.DarkSurfaceVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = SliderHorizontalPadding)
                    .semantics { contentDescription = sliderDesc },
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }

        Text(
            text = stringResource(R.string.reader_page_progress_pattern, displayPage, totalPages),
            style = ManhwareadTypography.labelMedium,
            color = ManhwareadPalette.DarkOnSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderControlsRow(
    state: ReaderUiState,
    onToggleOverlay: () -> Unit,
    onSelectMode: (ReaderMode) -> Unit,
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BarVerticalPadding),
    ) {
        OverlayToggleButton(
            showOverlay = state.showOverlay,
            onClick = onToggleOverlay,
        )

        Box {
            ModeSelectButton(
                currentMode = state.mode,
                onClick = { modeMenuExpanded = true },
            )
            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false },
                modifier = Modifier.background(ManhwareadPalette.DarkSurface),
            ) {
                ReaderMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(mode.labelRes()),
                                style = ManhwareadTypography.bodyMedium,
                                color = if (mode == state.mode) {
                                    ManhwareadPalette.DarkPrimary
                                } else {
                                    ManhwareadPalette.DarkOnSurface
                                },
                            )
                        },
                        onClick = {
                            onSelectMode(mode)
                            modeMenuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayToggleButton(
    showOverlay: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = ManhwareadPalette.DarkPrimary
    val inactiveColor = ManhwareadPalette.DarkSecondary
    val bgColor = if (showOverlay) {
        activeColor.copy(alpha = ACTIVE_OVERLAY_BG_ALPHA)
    } else {
        inactiveColor.copy(alpha = INACTIVE_OVERLAY_BG_ALPHA)
    }
    val contentColor = if (showOverlay) activeColor else inactiveColor
    val description = stringResource(
        if (showOverlay) R.string.reader_overlay_hide else R.string.reader_overlay_show,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(ManhwareadShapes.Input)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
    ) {
        Icon(
            imageVector = Icons.Filled.Translate,
            contentDescription = description,
            tint = contentColor,
        )
        Text(
            text = description,
            style = ManhwareadTypography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(start = BarVerticalPadding),
        )
    }
}

@Composable
private fun ModeSelectButton(
    currentMode: ReaderMode,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(ManhwareadShapes.Input)
            .background(ManhwareadPalette.DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
    ) {
        Text(
            text = stringResource(currentMode.labelRes()),
            style = ManhwareadTypography.labelMedium,
            color = ManhwareadPalette.DarkOnSurface,
        )
    }
}

@Composable
fun ReaderMinimalInfoStrip(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 0) return
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val totalPages = pageCount.coerceAtLeast(1)
    val displayPage = (currentPage + 1).coerceIn(1, totalPages)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomInset + InfoStripBottomOffset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(InfoStripRadius))
                .background(ManhwareadPalette.DarkBackground.copy(alpha = INFO_STRIP_ALPHA))
                .padding(horizontal = InfoStripHorizontalPadding, vertical = InfoStripVerticalPadding),
        ) {
            Text(
                text = stringResource(R.string.reader_page_progress_pattern, displayPage, totalPages),
                style = ManhwareadTypography.bodySmall,
                color = Color.White.copy(alpha = BAR_SURFACE_ALPHA),
            )
        }
    }
}
