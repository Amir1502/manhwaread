package com.manhwaread.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Label
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.manhwaread.core.designsystem.ManhwareadPalette
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.ManhwareadTypography

// Плавающие панели читалки (спека Этапа 8): плоский фон #121214 α0.92,
// отступы safeDrawing (вырезы камеры, горизонтальные бары в ландшафте).
// Системные бары учитываются «без учёта видимости»: панели не прыгают,
// пока статус-бар анимированно появляется вместе с ними.

private val BarHorizontalPadding = 4.dp
private val BarVerticalPadding = 4.dp
private val TitleHorizontalPadding = 8.dp
private val PageNumberMinWidth = 32.dp
private val ControlsSpacing = 4.dp
private val ControlHorizontalPadding = 12.dp
private val ControlMinHeight = 40.dp
private val ModeArrowSpacing = 2.dp
private val InfoStripBottomOffset = 8.dp
private val InfoStripHorizontalPadding = 10.dp
private val InfoStripVerticalPadding = 3.dp
private const val BAR_BACKGROUND_ALPHA = 0.92f
private const val INFO_STRIP_TEXT_ALPHA = 0.7f
private const val INFO_STRIP_BACKGROUND_ALPHA = 0.4f
private const val DISABLED_CONTROL_ALPHA = 0.38f
private const val DISABLED_SEGMENT_BACKGROUND_ALPHA = 0.12f

private val ReaderBarColor = ManhwareadPalette.DarkBackground.copy(alpha = BAR_BACKGROUND_ALPHA)

/** Состояние нижней панели: срез [ReaderUiState] и контекст главы от хоста. */
data class ReaderBottomBarState(
    val currentPage: Int,
    val pageCount: Int,
    val mode: ReaderMode,
    val showOverlay: Boolean,
    val hasOverlays: Boolean,
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    val hasToc: Boolean = false,
)

/** Колбэки нижней панели: сгруппированы, чтобы сигнатура оставалась читаемой. */
data class ReaderBottomBarActions(
    val onPageSelected: (Int) -> Unit,
    val onSelectMode: (ReaderMode) -> Unit,
    val onShowOverlay: (Boolean) -> Unit,
    val onPreviousChapter: () -> Unit = {},
    val onNextChapter: () -> Unit = {},
    val onOpenToc: () -> Unit = {},
)

// Отступы панели: safeDrawing ∪ системные бары независимо от их видимости.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun readerBarInsets(sides: WindowInsetsSides): WindowInsets =
    WindowInsets.safeDrawing.union(WindowInsets.systemBarsIgnoringVisibility).only(sides)

/**
 * Верхняя панель: «назад», название тайтла, подзаголовок «Том 7 · Глава 302»
 * и закладка текущей страницы ([onToggleBookmark] = null — закладки недоступны).
 */
@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String?,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Surface без клика перехватывает касания: тап мимо кнопок не уходит в страницу.
    Surface(
        color = ReaderBarColor,
        contentColor = ManhwareadPalette.DarkOnSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(readerBarInsets(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = TitleHorizontalPadding),
            ) {
                Text(
                    text = title,
                    style = ManhwareadTypography.titleMedium,
                    color = ManhwareadPalette.DarkOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = ManhwareadTypography.bodySmall,
                        color = ManhwareadPalette.DarkOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onToggleBookmark != null) {
                BookmarkButton(isBookmarked = isBookmarked, onClick = onToggleBookmark)
            }
        }
    }
}

@Composable
private fun BookmarkButton(isBookmarked: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = stringResource(
                if (isBookmarked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add,
            ),
            tint = if (isBookmarked) ManhwareadPalette.DarkPrimary else ManhwareadPalette.DarkOnSurface,
        )
    }
}

/**
 * Нижняя панель. Ряд 1: «‹ глава | номер | слайдер | всего | глава ›» — в режиме
 * «Справа налево» ряд зеркалится целиком (слайдер растёт справа налево).
 * Ряд 2: режим чтения, оглавление и тумблер «Оригинал | Перевод».
 */
@Composable
fun ReaderBottomBar(
    state: ReaderBottomBarState,
    actions: ReaderBottomBarActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ReaderBarColor,
        contentColor = ManhwareadPalette.DarkOnSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(readerBarInsets(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
        ) {
            val rowDirection = if (state.mode.isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
            CompositionLocalProvider(LocalLayoutDirection provides rowDirection) {
                ReaderPageRow(state = state, actions = actions)
            }
            ReaderControlsRow(state = state, actions = actions)
        }
    }
}

@Composable
private fun ReaderPageRow(state: ReaderBottomBarState, actions: ReaderBottomBarActions) {
    // Значение во время перетаскивания живёт локально: страница не меняется,
    // пока палец на бегунке; переход — один раз, после отпускания.
    var dragValue by remember(state.pageCount) { mutableStateOf<Float?>(null) }
    val iconColors = IconButtonDefaults.iconButtonColors(
        contentColor = ManhwareadPalette.DarkOnSurface,
        disabledContentColor = ManhwareadPalette.DarkOnSurface.copy(alpha = DISABLED_CONTROL_ALPHA),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = actions.onPreviousChapter, enabled = state.hasPreviousChapter, colors = iconColors) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.reader_chapter_previous),
            )
        }
        if (isPageSliderVisible(state.pageCount)) {
            val sliderValue = dragValue ?: state.currentPage.toFloat()
            PageNumber(page = sliderValueToPage(sliderValue, state.pageCount) + 1)
            ReaderPageSlider(
                value = sliderValue,
                pageCount = state.pageCount,
                onValueChange = { value -> dragValue = value },
                onValueChangeFinished = {
                    dragValue?.let { value -> actions.onPageSelected(sliderValueToPage(value, state.pageCount)) }
                    dragValue = null
                },
                modifier = Modifier.weight(1f),
            )
            PageNumber(page = state.pageCount)
        } else {
            // Одна страница: слайдер не нужен, показываем «1 / 1».
            Text(
                text = stringResource(R.string.reader_page_progress_pattern, 1, state.pageCount.coerceAtLeast(1)),
                style = ManhwareadTypography.labelMedium,
                color = ManhwareadPalette.DarkOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = actions.onNextChapter, enabled = state.hasNextChapter, colors = iconColors) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.reader_chapter_next),
            )
        }
    }
}

@Composable
private fun PageNumber(page: Int) {
    Text(
        text = page.toString(),
        style = ManhwareadTypography.labelMedium,
        color = ManhwareadPalette.DarkOnSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = PageNumberMinWidth),
    )
}

// Слайдер страниц с «пузырём» номера над бегунком во время перетаскивания.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderPageSlider(
    value: Float,
    pageCount: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Деления шагов прозрачные: на сотне страниц они сливаются в пунктир.
    val colors = SliderDefaults.colors(
        thumbColor = ManhwareadPalette.DarkPrimary,
        activeTrackColor = ManhwareadPalette.DarkPrimary,
        inactiveTrackColor = ManhwareadPalette.DarkSurfaceVariant,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
    val description = stringResource(R.string.reader_slider_description)
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = description },
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        steps = pageSliderSteps(pageCount),
        thumb = { sliderState ->
            Label(
                label = {
                    PlainTooltip(
                        containerColor = ManhwareadPalette.DarkPrimary,
                        contentColor = ManhwareadPalette.DarkOnPrimary,
                    ) {
                        Text(
                            text = (sliderValueToPage(sliderState.value, pageCount) + 1).toString(),
                            style = ManhwareadTypography.labelMedium,
                        )
                    }
                },
                interactionSource = interactionSource,
            ) {
                SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors)
            }
        },
        valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
    )
}

@Composable
private fun ReaderControlsRow(state: ReaderBottomBarState, actions: ReaderBottomBarActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ControlsSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BarHorizontalPadding),
    ) {
        ReaderModeMenu(currentMode = state.mode, onSelectMode = actions.onSelectMode)
        if (state.hasToc) {
            IconButton(onClick = actions.onOpenToc) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.reader_toc),
                    tint = ManhwareadPalette.DarkOnSurface,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        OverlaySegmentedToggle(
            showOverlay = state.showOverlay,
            enabled = state.hasOverlays,
            onShowOverlay = actions.onShowOverlay,
        )
    }
}

@Composable
private fun ReaderModeMenu(currentMode: ReaderMode, onSelectMode: (ReaderMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.reader_mode_selector)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ModeArrowSpacing),
            modifier = Modifier
                .heightIn(min = ControlMinHeight)
                .clip(ManhwareadShapes.Input)
                .background(ManhwareadPalette.DarkSurfaceVariant)
                .clickable(onClickLabel = menuLabel, role = Role.DropdownList) { expanded = true }
                .padding(start = ControlHorizontalPadding, end = ControlHorizontalPadding / 2),
        ) {
            Text(
                text = stringResource(currentMode.labelRes()),
                style = ManhwareadTypography.labelMedium,
                color = ManhwareadPalette.DarkOnSurface,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = ManhwareadPalette.DarkOnSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ManhwareadPalette.DarkSurface),
        ) {
            ReaderMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(mode.labelRes()),
                            style = ManhwareadTypography.bodyMedium,
                            color = if (mode == currentMode) {
                                ManhwareadPalette.DarkPrimary
                            } else {
                                ManhwareadPalette.DarkOnSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectMode(mode)
                    },
                )
            }
        }
    }
}

// Сегментный тумблер «Оригинал | Перевод»: активен, только если у главы есть
// векторный слой перевода (OverlaySpec); иначе выбран «Оригинал» и тумблер приглушён.
@Composable
private fun OverlaySegmentedToggle(
    showOverlay: Boolean,
    enabled: Boolean,
    onShowOverlay: (Boolean) -> Unit,
) {
    val translationSelected = enabled && showOverlay
    val unavailable = stringResource(R.string.reader_translation_unavailable)
    Row(
        modifier = Modifier
            .alpha(if (enabled) 1f else DISABLED_CONTROL_ALPHA)
            .clip(ManhwareadShapes.Input)
            .background(ManhwareadPalette.DarkSurfaceVariant)
            .selectableGroup()
            .semantics { if (!enabled) stateDescription = unavailable },
    ) {
        OverlaySegment(
            label = stringResource(R.string.reader_overlay_original),
            selected = !translationSelected,
            enabled = enabled,
            onClick = { onShowOverlay(false) },
        )
        OverlaySegment(
            label = stringResource(R.string.reader_overlay_translation),
            selected = translationSelected,
            enabled = enabled,
            onClick = { onShowOverlay(true) },
        )
    }
}

@Composable
private fun OverlaySegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // Недоступный тумблер — нейтральный: акцентный цвет только у активного выбора.
    val selectedBackground = if (enabled) {
        ManhwareadPalette.DarkPrimary
    } else {
        ManhwareadPalette.DarkOnSurface.copy(alpha = DISABLED_SEGMENT_BACKGROUND_ALPHA)
    }
    val selectedText = if (enabled) ManhwareadPalette.DarkOnPrimary else ManhwareadPalette.DarkOnSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = ControlMinHeight)
            .clip(ManhwareadShapes.Input)
            .background(if (selected) selectedBackground else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ControlHorizontalPadding),
    ) {
        Text(
            text = label,
            style = ManhwareadTypography.labelMedium,
            color = if (selected) selectedText else ManhwareadPalette.DarkOnSurface,
        )
    }
}

/**
 * Инфо-полоса вебтуна при скрытых панелях: «12 / 86 · 74% · 20:41».
 * Без обработчиков касаний — тапы и прокрутка проходят к ленте.
 */
@Composable
fun ReaderInfoStrip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(bottom = InfoStripBottomOffset)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = INFO_STRIP_BACKGROUND_ALPHA))
            .padding(horizontal = InfoStripHorizontalPadding, vertical = InfoStripVerticalPadding),
    ) {
        Text(
            text = text,
            style = ManhwareadTypography.labelSmall,
            color = Color.White.copy(alpha = INFO_STRIP_TEXT_ALPHA),
            maxLines = 1,
        )
    }
}
