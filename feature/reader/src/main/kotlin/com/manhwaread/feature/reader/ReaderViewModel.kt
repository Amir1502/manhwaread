package com.manhwaread.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// Клампинг с учётом режима: в вебтуне высота вьюпорта равна высоте контента
// (вертикальное перемещение выполняет скролл ленты, offsetY страницы — 0).
private fun ViewportTransform.clampedFor(state: ReaderUiState, page: ReaderPage, newScale: Float): ViewportTransform {
    val viewHeight = if (state.mode.isContinuousWebtoon) {
        page.heightPx * newScale
    } else {
        state.viewportHeightPx
    }
    return clamped(page.widthPx, page.heightPx, state.viewportWidthPx, viewHeight)
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val contentLoader: ReaderContentLoader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // initialPageIndex — позиция из истории чтения (ФАЗА 15): лента/пейджер
    // стартуют с сохранённой страницы через существующий механизм scrollTarget.
    fun openChapter(chapterDir: File, initialPageIndex: Int = 0) {
        _uiState.update { state -> state.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { contentLoader.loadChapter(chapterDir) }.fold(
                onSuccess = { chapter ->
                    val startPage = if (chapter.pages.isEmpty()) {
                        0
                    } else {
                        initialPageIndex.coerceIn(0, chapter.pages.lastIndex)
                    }
                    _uiState.update { state ->
                        state.copy(
                            chapter = chapter,
                            isLoading = false,
                            loadError = null,
                            transforms = emptyMap(),
                            currentPageIndex = startPage,
                            scrollTarget = startPage.takeIf { page -> page > 0 },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(isLoading = false, loadError = error.message ?: error.javaClass.simpleName)
                    }
                },
            )
        }
    }

    fun setViewportSize(widthPx: Float, heightPx: Float) {
        _uiState.update { state ->
            if (state.viewportWidthPx == widthPx && state.viewportHeightPx == heightPx) {
                state
            } else {
                state.copy(viewportWidthPx = widthPx, viewportHeightPx = heightPx)
            }
        }
    }

    // Смена режима сбрасывает зум: базовый масштаб в режимах разный.
    fun setMode(mode: ReaderMode) {
        _uiState.update { state -> state.copy(mode = mode, transforms = emptyMap()) }
    }

    fun toggleOverlay() {
        _uiState.update { state -> state.copy(showOverlay = !state.showOverlay) }
    }

    fun setCurrentPage(pageIndex: Int) {
        _uiState.update { state ->
            val pages = state.chapter?.pages ?: return@update state
            val coerced = pageIndex.coerceIn(0, pages.lastIndex)
            if (coerced == state.currentPageIndex) {
                state
            } else {
                state.copy(currentPageIndex = coerced)
            }
        }
    }

    fun onZoom(pageIndex: Int, factor: Float, focusX: Float, focusY: Float) {
        updateTransform(pageIndex) { state, page, current, base ->
            val focus = state.effectiveFocusY(focusY)
            current.zoomAround(factor, focusX, focus, base)
                .let { next -> next.clampedFor(state, page, next.scale) }
        }
    }

    fun onPan(pageIndex: Int, deltaX: Float, deltaY: Float) {
        updateTransform(pageIndex) { state, page, current, base ->
            val delta = state.effectiveFocusY(deltaY)
            current.panBy(deltaX, delta)
                .let { next -> next.clampedFor(state, page, next.scale) }
        }
    }

    // Двойной тап: увеличение 2.5x вокруг точки тапа либо возврат к базовому.
    fun onDoubleTapZoom(pageIndex: Int, focusX: Float, focusY: Float) {
        updateTransform(pageIndex) { state, page, current, base ->
            val isZoomed = current.scale > base * ZOOMED_EPSILON
            val targetScale = if (isZoomed) base else base * DOUBLE_TAP_ZOOM
            val factor = targetScale / current.scale
            val focus = state.effectiveFocusY(focusY)
            current.zoomAround(factor, focusX, focus, base)
                .let { next -> next.clampedFor(state, page, next.scale) }
        }
    }

    // Тап по странице: попадание по баблу открывает карточку «оригинал + перевод».
    // Промах сначала закрывает открытую карточку и только затем переключает
    // видимость панелей читалки (immersive-режим полного экрана).
    fun onTap(pageIndex: Int, x: Float, y: Float) {
        val state = _uiState.value
        val page = state.chapter?.pages?.getOrNull(pageIndex) ?: return
        val hit = hitTestBubble(x, y, state.transformFor(pageIndex), page.bubbles)
        _uiState.update { current ->
            when {
                hit != null -> current.copy(
                    selectedBubble = SelectedBubble(
                        bubbleId = hit.bubbleId,
                        originalText = hit.originalText,
                        translatedText = hit.translatedText,
                    ),
                )
                current.selectedBubble != null -> current.copy(selectedBubble = null)
                else -> current.copy(chromeVisible = !current.chromeVisible)
            }
        }
    }

    fun dismissBubbleSheet() {
        _uiState.update { state -> state.copy(selectedBubble = null) }
    }

    // Внешний запрос прокрутки (слайдер): отдельная ячейка scrollTarget,
    // чтобы репорт текущей страницы не отменял анимацию прокрутки.
    fun requestScrollToPage(pageIndex: Int) {
        _uiState.update { state -> state.copy(scrollTarget = pageIndex) }
    }

    fun consumeScrollTarget() {
        _uiState.update { state -> state.copy(scrollTarget = null) }
    }

    // В вебтуне вертикаль отдаёт скроллу ленты — фокус/дельта Y обнуляются.
    private fun ReaderUiState.effectiveFocusY(valueY: Float): Float =
        if (mode.isContinuousWebtoon) 0f else valueY

    private inline fun updateTransform(
        pageIndex: Int,
        compute: (ReaderUiState, ReaderPage, ViewportTransform, Float) -> ViewportTransform,
    ) {
        _uiState.update { state ->
            val page = state.chapter?.pages?.getOrNull(pageIndex) ?: return@update state
            val current = state.transformFor(pageIndex)
            val base = state.baseScaleFor(pageIndex)
            val next = compute(state, page, current, base)
            if (next == current) {
                state
            } else {
                state.copy(transforms = state.transforms + (pageIndex to next))
            }
        }
    }

    private companion object {
        const val DOUBLE_TAP_ZOOM = 2.5f
        const val ZOOMED_EPSILON = 1.01f
    }
}
