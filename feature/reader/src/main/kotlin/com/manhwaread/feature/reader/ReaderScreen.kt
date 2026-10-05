package com.manhwaread.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manhwaread.core.designsystem.ManhwareadPalette
import com.manhwaread.core.designsystem.ManhwareadShapes
import com.manhwaread.core.designsystem.ManhwareadTypography
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

private val SheetHorizontalPadding = 16.dp
private val SheetBottomPadding = 24.dp
private val SheetBlockSpacing = 8.dp
private const val CORRUPT_PAGE_HEIGHT_DP = 240

// Тап по краю вебтуна прокручивает ленту на 80% высоты экрана: строка
// у края остаётся видимой и служит ориентиром после прокрутки.
private const val WEBTOON_TAP_SCROLL_FRACTION = 0.8f

// Экран читалки: четыре режима (вебтун-лента, вертикальный и горизонтальные
// пейджеры), зум до 5x, векторный слой перевода, карточка бабла по тапу.
// initialPageIndex/onProgress — интеграция с историей чтения (ФАЗА 15);
// navigation/navigationActions — контекст тайтла от хоста (Этап 8): оглавление,
// соседние главы, закладки и сохранённый режим чтения.
@Composable
fun ReaderScreen(
    chapterDir: File?,
    onBack: () -> Unit,
    initialPageIndex: Int = 0,
    onProgress: (pageIndex: Int) -> Unit = {},
    navigation: ReaderNavigation = ReaderNavigation(),
    navigationActions: ReaderNavigationActions = ReaderNavigationActions(),
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tocVisible by rememberSaveable { mutableStateOf(false) }
    // Сохранённый режим тайтла применяется один раз: после поворота экрана
    // выбор пользователя в текущей сессии не перетирается.
    var appliedPreferredMode by rememberSaveable { mutableStateOf<ReaderMode?>(null) }
    ImmersiveSystemBarsEffect(barsVisible = state.chromeVisible)
    LaunchedEffect(chapterDir, initialPageIndex) {
        if (chapterDir != null) {
            viewModel.openChapter(chapterDir, initialPageIndex)
        }
    }
    LaunchedEffect(navigation.preferredMode) {
        val preferred = navigation.preferredMode
        if (preferred != null && preferred != appliedPreferredMode) {
            appliedPreferredMode = preferred
            viewModel.setMode(preferred)
        }
    }
    LaunchedEffect(state.chapter, state.currentPageIndex) {
        if (state.chapter != null) {
            onProgress(state.currentPageIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewModel.setViewportSize(size.width.toFloat(), size.height.toFloat())
            },
    ) {
        ReaderContent(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize())
        ReaderChrome(
            state = state,
            navigation = navigation,
            onBack = onBack,
            actions = ReaderChromeActions(
                onPageSelected = viewModel::jumpToPage,
                onSelectMode = { mode ->
                    viewModel.setMode(mode)
                    navigationActions.onModeChange(mode)
                },
                onShowOverlay = viewModel::setShowOverlay,
                onOpenChapter = navigationActions.onOpenChapter,
                onToggleBookmark = navigationActions.onToggleBookmark,
                onOpenToc = { tocVisible = true },
            ),
        )

        state.selectedBubble?.let { bubble ->
            ReaderBubbleSheet(bubble = bubble, onDismiss = viewModel::dismissBubbleSheet)
        }
        if (tocVisible) {
            ReaderTocSheet(
                chapters = navigation.chapters,
                currentChapterId = navigation.currentChapterId,
                onSelectChapter = { chapterId ->
                    tocVisible = false
                    if (chapterId != navigation.currentChapterId) {
                        navigationActions.onOpenChapter(chapterId)
                    }
                },
                onDismiss = { tocVisible = false },
            )
        }
    }
}

// Колбэки панелей читалки (внутренние): ViewModel + действия хоста.
private class ReaderChromeActions(
    val onPageSelected: (Int) -> Unit,
    val onSelectMode: (ReaderMode) -> Unit,
    val onShowOverlay: (Boolean) -> Unit,
    val onOpenChapter: (chapterId: Long) -> Unit,
    val onToggleBookmark: (pageIndex: Int) -> Unit,
    val onOpenToc: () -> Unit,
)

// Панели и инфо-полоса поверх контента. Инфо-полоса — только в вебтуне при
// скрытых панелях; батарея и часы активны, лишь пока она в композиции.
@Composable
private fun BoxScope.ReaderChrome(
    state: ReaderUiState,
    navigation: ReaderNavigation,
    onBack: () -> Unit,
    actions: ReaderChromeActions,
) {
    val chapter = state.chapter
    val pageCount = chapter?.pages?.size ?: 0

    AnimatedVisibility(
        visible = !state.chromeVisible && state.mode.isContinuousWebtoon && pageCount > 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val battery = rememberBatteryPercent()
        val clock = rememberClockText()
        ReaderInfoStrip(text = formatInfoStrip(state.currentPageIndex, pageCount, battery, clock))
    }

    AnimatedVisibility(
        visible = state.chromeVisible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        val chapterName = navigation.currentChapter?.title ?: chapter?.title
        ReaderTopBar(
            title = navigation.mangaTitle ?: chapter?.title.orEmpty(),
            subtitle = chapterName?.takeIf { navigation.mangaTitle != null }?.let(::chapterSubtitle),
            isBookmarked = state.currentPageIndex in navigation.bookmarkedPages,
            onBack = onBack,
            onToggleBookmark = if (navigation.supportsBookmarks && pageCount > 0) {
                { actions.onToggleBookmark(state.currentPageIndex) }
            } else {
                null
            },
        )
    }

    AnimatedVisibility(
        visible = state.chromeVisible && chapter != null && pageCount > 0,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        ReaderBottomBar(
            state = ReaderBottomBarState(
                currentPage = state.currentPageIndex,
                pageCount = pageCount,
                mode = state.mode,
                showOverlay = state.showOverlay,
                hasOverlays = chapter?.hasOverlays == true,
                hasPreviousChapter = navigation.previousChapter != null,
                hasNextChapter = navigation.nextChapter != null,
                hasToc = navigation.chapters.isNotEmpty(),
            ),
            actions = ReaderBottomBarActions(
                onPageSelected = actions.onPageSelected,
                onSelectMode = actions.onSelectMode,
                onShowOverlay = actions.onShowOverlay,
                onPreviousChapter = { navigation.previousChapter?.let { item -> actions.onOpenChapter(item.chapterId) } },
                onNextChapter = { navigation.nextChapter?.let { item -> actions.onOpenChapter(item.chapterId) } },
                onOpenToc = actions.onOpenToc,
            ),
        )
    }
}

// Автоскрытие панелей: начало прокрутки/перелистывания жестом скрывает их.
// Программная прокрутка (слайдер, тап по краю) DragInteraction не порождает.
@Composable
private fun HideChromeOnDragEffect(interactionSource: InteractionSource, onDragStart: () -> Unit) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                currentOnDragStart()
            }
        }
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ManhwareadPalette.DarkPrimary)
        }
        state.loadError != null -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = state.loadError.orEmpty(),
                style = ManhwareadTypography.bodyLarge,
                color = ManhwareadPalette.DarkError,
            )
        }
        state.chapter != null -> when {
            state.mode.isContinuousWebtoon -> WebtoonList(state = state, viewModel = viewModel)
            state.mode == ReaderMode.VERTICAL -> VerticalPagePager(state = state, viewModel = viewModel)
            else -> HorizontalPagePager(state = state, viewModel = viewModel)
        }
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.reader_empty),
                style = ManhwareadTypography.bodyLarge,
                color = ManhwareadPalette.DarkOnSurfaceVariant,
            )
        }
    }
}

// Вебтун: непрерывная лента без швов — интервалы между элементами нулевые,
// высота элемента точно равна высоте страницы в текущем масштабе.
@Composable
private fun WebtoonList(state: ReaderUiState, viewModel: ReaderViewModel) {
    val pages = state.chapter?.pages ?: return
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    LaunchedEffect(listState) {
        if (state.currentPageIndex in pages.indices) {
            listState.scrollToItem(state.currentPageIndex)
        }
    }
    LaunchedEffect(state.scrollTarget) {
        val target = state.scrollTarget
        if (target != null && target in pages.indices) {
            if (state.scrollTargetAnimated) listState.animateScrollToItem(target) else listState.scrollToItem(target)
        }
        if (target != null) {
            viewModel.consumeScrollTarget()
        }
    }
    WebtoonScrollStepEffect(state = state, listState = listState, viewModel = viewModel)
    HideChromeOnDragEffect(interactionSource = listState.interactionSource, onDragStart = viewModel::hideChrome)
    LaunchedEffect(listState) {
        snapshotFlow { findCenterVisibleItemIndex(listState.layoutInfo) }
            .distinctUntilChanged()
            .collect { index -> viewModel.setCurrentPage(index) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(count = pages.size, key = { position -> pages[position].index }) { position ->
            val page = pages[position]
            val transform = state.transformFor(page.index)
            val itemHeight = if (page.isCorrupted) {
                CORRUPT_PAGE_HEIGHT_DP.dp
            } else {
                with(density) { (page.heightPx * transform.scale).toDp() }
            }
            Box(modifier = Modifier.fillMaxWidth().height(itemHeight)) {
                ReaderPageView(
                    page = page,
                    transform = transform,
                    baseScale = state.baseScaleFor(page.index),
                    showOverlay = state.showOverlay,
                    actions = ReaderPageActions(
                        onZoom = { factor, focusX, _ -> viewModel.onZoom(page.index, factor, focusX, 0f) },
                        onPan = { deltaX, _ -> viewModel.onPan(page.index, deltaX, 0f) },
                        onTap = { x, y ->
                            // Зона тапа считается во вьюпорте: к локальной Y страницы
                            // добавляется смещение элемента в ленте.
                            val itemOffset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { info -> info.index == position }?.offset ?: 0
                            viewModel.onTap(page.index, x, y, viewportY = itemOffset + y)
                        },
                        onDoubleTapZoom = { x, _ -> viewModel.onDoubleTapZoom(page.index, x, 0f) },
                    ),
                )
            }
        }
    }
}

// Тап по краю ленты при скрытых панелях: плавная прокрутка на долю экрана.
// Каждый запрос уникален (token), выполненный — сбрасывается в ViewModel.
@Composable
private fun WebtoonScrollStepEffect(state: ReaderUiState, listState: LazyListState, viewModel: ReaderViewModel) {
    val step = state.scrollStep
    LaunchedEffect(step) {
        if (step != null) {
            listState.animateScrollBy(step.direction * state.viewportHeightPx * WEBTOON_TAP_SCROLL_FRACTION)
            viewModel.consumeScrollStep(step)
        }
    }
}

@Composable
private fun HorizontalPagePager(state: ReaderUiState, viewModel: ReaderViewModel) {
    val pages = state.chapter?.pages ?: return
    val initialPage = state.currentPageIndex.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    PagerScrollEffects(state = state, viewModel = viewModel, pagerPage = { pagerState.currentPage }) { target, animated ->
        if (animated) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
    }
    HideChromeOnDragEffect(interactionSource = pagerState.interactionSource, onDragStart = viewModel::hideChrome)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.setCurrentPage(page) }
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = state.mode.isRtl,
        modifier = Modifier.fillMaxSize(),
    ) { position ->
        PagerPage(state = state, viewModel = viewModel, page = pages[position])
    }
}

@Composable
private fun VerticalPagePager(state: ReaderUiState, viewModel: ReaderViewModel) {
    val pages = state.chapter?.pages ?: return
    val initialPage = state.currentPageIndex.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    PagerScrollEffects(state = state, viewModel = viewModel, pagerPage = { pagerState.currentPage }) { target, animated ->
        if (animated) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
    }
    HideChromeOnDragEffect(interactionSource = pagerState.interactionSource, onDragStart = viewModel::hideChrome)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.setCurrentPage(page) }
    }
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { position ->
        PagerPage(state = state, viewModel = viewModel, page = pages[position])
    }
}

// Внешний запрос прокрутки (слайдер, тап по краю): отдельная ячейка scrollTarget,
// чтобы репорт текущей страницы во время анимации не перезапускал LaunchedEffect.
// Слайдер переходит мгновенно, тап по краю листает с анимацией.
@Composable
private fun PagerScrollEffects(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    pagerPage: () -> Int,
    scrollToPage: suspend (target: Int, animated: Boolean) -> Unit,
) {
    val pages = state.chapter?.pages ?: return
    LaunchedEffect(state.scrollTarget) {
        val target = state.scrollTarget
        if (target != null && target in pages.indices && pagerPage() != target) {
            scrollToPage(target, state.scrollTargetAnimated)
        }
        if (target != null) {
            viewModel.consumeScrollTarget()
        }
    }
}

@Composable
private fun PagerPage(state: ReaderUiState, viewModel: ReaderViewModel, page: ReaderPage) {
    ReaderPageView(
        page = page,
        transform = state.transformFor(page.index),
        baseScale = state.baseScaleFor(page.index),
        showOverlay = state.showOverlay,
        actions = ReaderPageActions(
            onZoom = { factor, x, y -> viewModel.onZoom(page.index, factor, x, y) },
            onPan = { deltaX, deltaY -> viewModel.onPan(page.index, deltaX, deltaY) },
            onTap = { x, y -> viewModel.onTap(page.index, x, y) },
            onDoubleTapZoom = { x, y -> viewModel.onDoubleTapZoom(page.index, x, y) },
        ),
    )
}

// Карточка бабла по тапу: оригинал и перевод (DoD).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBubbleSheet(bubble: SelectedBubble, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ManhwareadPalette.DarkSurface,
        shape = ManhwareadShapes.BottomSheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetHorizontalPadding)
                .padding(bottom = SheetBottomPadding),
        ) {
            Text(
                text = stringResource(R.string.reader_bubble_original),
                style = ManhwareadTypography.labelMedium,
                color = ManhwareadPalette.DarkOnSurfaceVariant,
            )
            Text(
                text = bubble.originalText,
                style = ManhwareadTypography.bodyLarge,
                color = ManhwareadPalette.DarkOnSurface,
            )
            HorizontalDivider(
                color = ManhwareadPalette.DarkSurfaceVariant,
                modifier = Modifier.padding(vertical = SheetBlockSpacing),
            )
            Text(
                text = stringResource(R.string.reader_bubble_translated),
                style = ManhwareadTypography.labelMedium,
                color = ManhwareadPalette.DarkOnSurfaceVariant,
            )
            Text(
                text = bubble.translatedText ?: stringResource(R.string.reader_no_translation),
                style = ManhwareadTypography.bodyLarge,
                color = ManhwareadPalette.DarkOnSurface,
            )
        }
    }
}
