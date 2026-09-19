package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import java.io.File

/** Прямоугольник в пикселях изображения: [left, top, right, bottom), right/bottom исключительно. */
data class TileRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left

    val height: Int get() = bottom - top
}

/** Строка оверлея в экранных координатах: то, что рисует OverlayLayerView. */
data class ProjectedOverlayLine(
    val bubbleId: String,
    val text: String,
    val x: Float,
    val baselineY: Float,
    val sizePx: Float,
    val letterSpacingPx: Float,
    val scaleX: Float,
    val colorArgb: Int,
    val fontFamily: String?,
)

// Маскирующая подложка переведённого бабла в экранных координатах:
// OverlayLayerView рисует её под строками перевода, закрывая оригинал.
data class ProjectedOverlayBackground(
    val bubbleId: String,
    val shape: BubbleMaskShape,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val polygon: List<PointF>,
    val colorArgb: Int,
)

// Колбэки жестов страницы: сгруппированы, чтобы уложиться в порог
// числа параметров detekt и не плодить лямбды в сигнатуре.
data class ReaderPageActions(
    val onZoom: (factor: Float, focusX: Float, focusY: Float) -> Unit,
    val onPan: (deltaX: Float, deltaY: Float) -> Unit,
    val onTap: (x: Float, y: Float) -> Unit,
    val onDoubleTapZoom: (x: Float, y: Float) -> Unit,
)

// Форма маскирующей подложки оверлея перевода: читалка закрывает бабл
// этой фигурой, чтобы перевод читался на чистом фоне (оригинал не виден).
enum class BubbleMaskShape { ELLIPSE, RECT, POLYGON }

// Область бабла для тапа: границы в пикселях страницы + пара текстов
// (оригинал и перевод) для карточки по тапу (DoD: «тап по баблу →
// оригинал + перевод»). Поля подложки (shape/polygon/fillColorArgb) —
// аддитивное расширение: старые chapter.json без них читаются с дефолтами.
data class BubbleHitArea(
    val bubbleId: String,
    val pageIndex: Int,
    val bounds: RectF,
    val originalText: String,
    val translatedText: String?,
    val shape: BubbleMaskShape = BubbleMaskShape.ELLIPSE,
    val polygon: List<PointF> = emptyList(),
    val fillColorArgb: Int? = null,
)

// Страница, готовая к рендеру: файл + размеры (нужны для раскладки ДО
// декодирования) + векторный слой перевода + области баблов.
// isCorrupted — аддитивный флаг: файл страницы не читается (битый/не
// изображение), размеры неизвестны (0×0); читалка показывает заглушку
// вместо падения всей главы.
data class ReaderPage(
    val index: Int,
    val imageFile: File,
    val widthPx: Int,
    val heightPx: Int,
    val overlays: List<OverlaySpec>,
    val bubbles: List<BubbleHitArea>,
    val isCorrupted: Boolean = false,
) {
    val aspectRatio: Float get() = widthPx.toFloat() / heightPx.toFloat()
}

// Глава, загруженная в читалку.
data class ReaderChapter(
    val title: String,
    val pages: List<ReaderPage>,
)

// Выбранный тапом бабл: контент нижней панели.
data class SelectedBubble(
    val bubbleId: String,
    val originalText: String,
    val translatedText: String?,
)

// UI-состояние читалки: контент, режим, видимость оверлея и панелей,
// трансформации страниц (зум/панорамирование живут здесь и переживают поворот экрана).
data class ReaderUiState(
    val chapter: ReaderChapter? = null,
    val mode: ReaderMode = ReaderMode.WEBTOON,
    val showOverlay: Boolean = true,
    val chromeVisible: Boolean = true,
    val currentPageIndex: Int = 0,
    val transforms: Map<Int, ViewportTransform> = emptyMap(),
    val viewportWidthPx: Float = 0f,
    val viewportHeightPx: Float = 0f,
    val selectedBubble: SelectedBubble? = null,
    val scrollTarget: Int? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
) {
    // Трансформация страницы: до первого зума — fit-to-width в вебтуне
    // (лента) и fit-to-screen в пейджерных режимах (страница видна целиком).
    fun transformFor(pageIndex: Int): ViewportTransform {
        transforms[pageIndex]?.let { return it }
        val page = chapter?.pages?.getOrNull(pageIndex) ?: return ViewportTransform.IDENTITY
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) {
            return ViewportTransform.IDENTITY
        }
        return if (mode.isContinuousWebtoon) {
            fitToWidth(page.widthPx, viewportWidthPx)
        } else {
            fitScreen(page.widthPx, page.heightPx, viewportWidthPx, viewportHeightPx)
        }
    }

    fun baseScaleFor(pageIndex: Int): Float {
        val page = chapter?.pages?.getOrNull(pageIndex) ?: return 1f
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) {
            return 1f
        }
        return if (mode.isContinuousWebtoon) {
            baseScaleFor(page.widthPx, viewportWidthPx)
        } else {
            minOf(viewportWidthPx / page.widthPx, viewportHeightPx / page.heightPx)
        }
    }
}
