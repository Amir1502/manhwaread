package com.manhwaread.feature.reader

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

private val SliderPadding = 12.dp
private val SheetHorizontalPadding = 16.dp
private val SheetBottomPadding = 24.dp
private val SheetBlockSpacing = 8.dp

// Экран читалки: четыре режима (вебтун-лента, вертикальный и горизонтальные
// пейджеры), зум до 5x, векторный слой перевода, карточка бабла по тапу.
// initialPageIndex/onProgress — интеграция с историей чтения (ФАЗА 15).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapterDir: File?,
    onBack: () -> Unit,
    initialPageIndex: Int = 0,
    onProgress: (pageIndex: Int) -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImmersiveSystemBarsEffect()
    LaunchedEffect(chapterDir, initialPageIndex) {
        if (chapterDir != null) {
            viewModel.openChapter(chapterDir, initialPageIndex)
        }
    }
    LaunchedEffect(state.chapter, state.currentPageIndex) {
        if (state.chapter != null) {
            onProgress(state.currentPageIndex)
        }
    }
    Scaffold(
        // Панели читалки рендерятся только при видимом chrome: тап мимо бабла
        // скрывает их, повторный тап возвращает (полноэкранное чтение).
        topBar = {
            if (state.chromeVisible) {
                TopAppBar(
                    title = { Text(state.chapter?.title.orEmpty()) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.reader_back),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            val chapter = state.chapter
            if (state.chromeVisible && chapter != null && chapter.pages.isNotEmpty()) {
                ReaderBottomBar(state = state, pageCount = chapter.pages.size, viewModel = viewModel)
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .onSizeChanged { size -> viewModel.setViewportSize(size.width.toFloat(), size.height.toFloat()) },
        ) {
            ReaderContent(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize())
            state.selectedBubble?.let { bubble ->
                ReaderBubbleSheet(bubble = bubble, onDismiss = viewModel::dismissBubbleSheet)
            }
        }
    }
}

// Immersive-полноэкран: пока читалка в композиции, системные бары скрыты,
// свайп показывает их временно; при выходе со экрана бары возвращаются.
@Composable
private fun ImmersiveSystemBarsEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { activityWindow -> WindowInsetsControllerCompat(activityWindow, view) }
        if (controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
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
            CircularProgressIndicator()
        }
        state.loadError != null -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = state.loadError.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
        state.chapter != null -> when {
            state.mode.isContinuousWebtoon -> WebtoonList(state = state, viewModel = viewModel)
            state.mode == ReaderMode.VERTICAL -> VerticalPagePager(state = state, viewModel = viewModel)
            else -> HorizontalPagePager(state = state, viewModel = viewModel)
        }
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.reader_empty), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// Высота элемента ленты для битой страницы (heightPx == 0): фиксированный
// плейсхолдер, чтобы сообщение об ошибке было видно и в вебтун-режиме.
private const val CORRUPT_PAGE_HEIGHT_DP = 240

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
            listState.animateScrollToItem(target)
        }
        if (target != null) {
            viewModel.consumeScrollTarget()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0 }
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
                        onTap = { x, y -> viewModel.onTap(page.index, x, y) },
                        onDoubleTapZoom = { x, _ -> viewModel.onDoubleTapZoom(page.index, x, 0f) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun HorizontalPagePager(state: ReaderUiState, viewModel: ReaderViewModel) {
    val pages = state.chapter?.pages ?: return
    val initialPage = state.currentPageIndex.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    PagerScrollEffects(state = state, viewModel = viewModel, pagerPage = { pagerState.currentPage }) { target ->
        pagerState.animateScrollToPage(target)
    }
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
    PagerScrollEffects(state = state, viewModel = viewModel, pagerPage = { pagerState.currentPage }) { target ->
        pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.setCurrentPage(page) }
    }
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { position ->
        PagerPage(state = state, viewModel = viewModel, page = pages[position])
    }
}

// Внешний запрос прокрутки (слайдер): отдельная ячейка scrollTarget, чтобы
// репорт текущей страницы во время анимации не перезапускал LaunchedEffect.
@Composable
private fun PagerScrollEffects(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    pagerPage: () -> Int,
    animateToPage: suspend (Int) -> Unit,
) {
    val pages = state.chapter?.pages ?: return
    LaunchedEffect(state.scrollTarget) {
        val target = state.scrollTarget
        if (target != null && target in pages.indices && pagerPage() != target) {
            animateToPage(target)
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

@Composable
private fun ReaderBottomBar(state: ReaderUiState, pageCount: Int, viewModel: ReaderViewModel) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        if (pageCount > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = state.currentPageIndex.toFloat(),
                    onValueChange = { value -> viewModel.setCurrentPage(value.toInt()) },
                    onValueChangeFinished = { viewModel.requestScrollToPage(state.currentPageIndex) },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = SliderPadding),
                )
                Text(
                    text = "${state.currentPageIndex + 1}/$pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = SliderPadding),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SliderPadding),
        ) {
            IconButton(onClick = viewModel::toggleOverlay) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = stringResource(
                        if (state.showOverlay) R.string.reader_overlay_hide else R.string.reader_overlay_show,
                    ),
                    tint = if (state.showOverlay) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Box {
                TextButton(onClick = { modeMenuExpanded = true }) {
                    Text(stringResource(state.mode.labelRes()))
                }
                DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                    ReaderMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.labelRes())) },
                            onClick = {
                                viewModel.setMode(mode)
                                modeMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// Карточка бабла по тапу: оригинал и перевод (DoD).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBubbleSheet(bubble: SelectedBubble, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetHorizontalPadding)
                .padding(bottom = SheetBottomPadding),
        ) {
            Text(
                text = stringResource(R.string.reader_bubble_original),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = bubble.originalText, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider(modifier = Modifier.padding(vertical = SheetBlockSpacing))
            Text(
                text = stringResource(R.string.reader_bubble_translated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = bubble.translatedText ?: stringResource(R.string.reader_no_translation),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
