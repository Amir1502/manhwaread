package com.manhwaread.feature.reader

import com.manhwaread.core.vision.OverlayLine
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.PointF
import com.manhwaread.core.vision.RectF
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ReaderViewModelTest {
    private val viewWidth = 1080f
    private val viewHeight = 2400f

    // viewModelScope работает на Dispatchers.Main.immediate: устанавливаем
    // тестовый Main-диспетчер (runTest разделяет его планировщик).
    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUpMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun chapter() = ReaderChapter(
        title = "Глава 1",
        pages = listOf(
            ReaderPage(
                index = 0,
                imageFile = File("p0.png"),
                widthPx = 800,
                heightPx = 1200,
                overlays = emptyList(),
                bubbles = listOf(
                    BubbleHitArea("b1", 0, RectF(0f, 0f, 400f, 300f), "원문", "перевод"),
                ),
            ),
            ReaderPage(
                index = 1,
                imageFile = File("p1.png"),
                widthPx = 800,
                heightPx = 20000,
                overlays = emptyList(),
                bubbles = emptyList(),
            ),
        ),
    )

    private fun viewModelReturning(result: ReaderChapter): ReaderViewModel {
        val loader = mockk<ReaderContentLoader>()
        coEvery { loader.loadChapter(any()) } returns result
        return ReaderViewModel(loader)
    }

    private fun viewModelFailing(message: String): ReaderViewModel {
        val loader = mockk<ReaderContentLoader>()
        coEvery { loader.loadChapter(any()) } throws IllegalStateException(message)
        return ReaderViewModel(loader)
    }

    // Открывает главу и дожидается загруженного состояния (StateFlow
    // конфлейтит промежуточные значения — ждём по предикату, а не по числу эмиссий).
    private suspend fun ReaderViewModel.openedChapter(): ReaderUiState {
        setViewportSize(viewWidth, viewHeight)
        openChapter(File("dir"))
        return try {
            withTimeout(STATE_TIMEOUT_MS) { uiState.first { it.chapter != null } }
        } catch (timeout: TimeoutCancellationException) {
            // Диагностика причины: loadError содержит текст ошибки загрузчика.
            throw AssertionError("chapter never loaded, state=${uiState.value}", timeout)
        }
    }

    @Test
    fun `openChapter loads title and pages`() = runTest {
        val viewModel = viewModelReturning(chapter())
        val state = viewModel.openedChapter()
        assertEquals("Глава 1", state.chapter?.title)
        assertEquals(2, state.chapter?.pages?.size)
        assertFalse(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun `openChapter failure surfaces loadError`() = runTest {
        val viewModel = viewModelFailing("каталог недоступен")
        viewModel.setViewportSize(viewWidth, viewHeight)
        viewModel.openChapter(File("dir"))
        val state = withTimeout(STATE_TIMEOUT_MS) {
            viewModel.uiState.first { !it.isLoading && it.loadError != null }
        }
        assertEquals("каталог недоступен", state.loadError)
        assertNull(state.chapter)
    }

    @Test
    fun `setMode resets transforms`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.onZoom(pageIndex = 0, factor = 2f, focusX = 100f, focusY = 100f)
        assertTrue(viewModel.uiState.value.transforms.containsKey(0))
        viewModel.setMode(ReaderMode.LTR)
        assertEquals(ReaderMode.LTR, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.transforms.isEmpty())
    }

    @Test
    fun `toggleOverlay flips visibility`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        assertTrue(viewModel.uiState.value.showOverlay)
        viewModel.toggleOverlay()
        assertFalse(viewModel.uiState.value.showOverlay)
        viewModel.toggleOverlay()
        assertTrue(viewModel.uiState.value.showOverlay)
    }

    @Test
    fun `zoom stops at five times base scale`() = runTest {
        val viewModel = viewModelReturning(chapter())
        val state = viewModel.openedChapter()
        val base = state.baseScaleFor(0)
        assertEquals(viewWidth / 800f, base, EPS)
        repeat(20) {
            viewModel.onZoom(pageIndex = 0, factor = 2f, focusX = viewWidth / 2f, focusY = 100f)
        }
        assertEquals(base * ViewportTransform.MAX_ZOOM, viewModel.uiState.value.transformFor(0).scale, EPS)
    }

    @Test
    fun `pan cannot drag image away from view`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.onZoom(pageIndex = 0, factor = 2f, focusX = viewWidth / 2f, focusY = 100f)
        viewModel.onPan(pageIndex = 0, deltaX = 10_000f, deltaY = 0f)
        val transform = viewModel.uiState.value.transformFor(0)
        // Ширина контента 800 * 2.7 = 2160 > 1080: offsetX обязан остаться в [-1080, 0].
        assertTrue(transform.offsetX <= 0f, "offsetX=${transform.offsetX}")
        assertTrue(
            transform.offsetX >= viewWidth - 800 * transform.scale,
            "offsetX=${transform.offsetX}",
        )
    }

    @Test
    fun `tap on bubble selects it with both texts`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        // Базовая трансформация: scale 1.35, offset 0; бабл (0..400, 0..300) px
        // изображения занимает (0..540, 0..405) экранных px.
        viewModel.onTap(pageIndex = 0, x = 100f, y = 100f)
        val selected = viewModel.uiState.value.selectedBubble
        assertEquals("b1", selected?.bubbleId)
        assertEquals("원문", selected?.originalText)
        assertEquals("перевод", selected?.translatedText)
    }

    @Test
    fun `tap outside bubbles clears selection`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.onTap(pageIndex = 0, x = 100f, y = 100f)
        viewModel.onTap(pageIndex = 0, x = 900f, y = 2000f)
        assertNull(viewModel.uiState.value.selectedBubble)
    }

    @Test
    fun `miss tap hides chrome and only central tap shows it again`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        assertTrue(viewModel.uiState.value.chromeVisible)
        // Тап мимо бабла при видимых панелях скрывает их — в любой зоне.
        viewModel.onTap(pageIndex = 0, x = 900f, y = 2000f)
        assertFalse(viewModel.uiState.value.chromeVisible)
        // Тап по нижней трети ленты панели не возвращает — это шаг прокрутки.
        viewModel.onTap(pageIndex = 0, x = 900f, y = 2000f)
        assertFalse(viewModel.uiState.value.chromeVisible)
        // Центральная треть экрана показывает панели.
        viewModel.onTap(pageIndex = 0, x = 540f, y = 1200f)
        assertTrue(viewModel.uiState.value.chromeVisible)
    }

    @Test
    fun `webtoon edge taps emit unique scroll steps`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.hideChrome()

        // Вторая страница сдвинута лентой: зона считается по viewportY, а не по y страницы.
        viewModel.onTap(pageIndex = 1, x = 800f, y = 50f, viewportY = 2300f)
        val forward = viewModel.uiState.value.scrollStep
        assertEquals(1, forward?.direction)

        viewModel.onTap(pageIndex = 1, x = 800f, y = 50f, viewportY = 100f)
        val backward = viewModel.uiState.value.scrollStep
        assertEquals(-1, backward?.direction)
        assertTrue((backward?.token ?: 0L) > (forward?.token ?: 0L))

        // Устаревший шаг не сбрасывает более новый запрос.
        viewModel.consumeScrollStep(forward!!)
        assertEquals(backward, viewModel.uiState.value.scrollStep)
        viewModel.consumeScrollStep(backward!!)
        assertNull(viewModel.uiState.value.scrollStep)
        assertNull(viewModel.uiState.value.scrollTarget)
    }

    @Test
    fun `pager edge taps turn pages and mirror in right to left`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.setMode(ReaderMode.LTR)
        viewModel.hideChrome()

        viewModel.onTap(pageIndex = 0, x = 1000f, y = 1200f)
        assertEquals(1, viewModel.uiState.value.scrollTarget)
        assertTrue(viewModel.uiState.value.scrollTargetAnimated)
        viewModel.consumeScrollTarget()

        // Первая страница: «назад» некуда — запроса прокрутки нет.
        viewModel.onTap(pageIndex = 0, x = 50f, y = 1200f)
        assertNull(viewModel.uiState.value.scrollTarget)

        viewModel.setMode(ReaderMode.RTL)
        viewModel.onTap(pageIndex = 0, x = 50f, y = 1200f)
        assertEquals(1, viewModel.uiState.value.scrollTarget)
        assertFalse(viewModel.uiState.value.chromeVisible)
    }

    @Test
    fun `hideChrome hides visible panels`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.hideChrome()
        assertFalse(viewModel.uiState.value.chromeVisible)
        viewModel.hideChrome()
        assertFalse(viewModel.uiState.value.chromeVisible)
    }

    @Test
    fun `jumpToPage moves instantly and coerces into range`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.jumpToPage(1)
        val state = viewModel.uiState.value
        assertEquals(1, state.currentPageIndex)
        assertEquals(1, state.scrollTarget)
        assertFalse(state.scrollTargetAnimated)

        viewModel.jumpToPage(99)
        assertEquals(1, viewModel.uiState.value.currentPageIndex)
        viewModel.jumpToPage(-3)
        assertEquals(0, viewModel.uiState.value.currentPageIndex)
    }

    @Test
    fun `setting the same mode keeps zoom`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.onDoubleTapZoom(pageIndex = 0, focusX = viewWidth / 2f, focusY = 200f)
        val zoomed = viewModel.uiState.value.transforms
        assertTrue(zoomed.isNotEmpty())

        viewModel.setMode(ReaderMode.WEBTOON)
        assertEquals(zoomed, viewModel.uiState.value.transforms)
        viewModel.setMode(ReaderMode.RTL)
        assertTrue(viewModel.uiState.value.transforms.isEmpty())
    }

    @Test
    fun `setShowOverlay sets explicit visibility`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.setShowOverlay(false)
        assertFalse(viewModel.uiState.value.showOverlay)
        viewModel.setShowOverlay(false)
        assertFalse(viewModel.uiState.value.showOverlay)
        viewModel.setShowOverlay(true)
        assertTrue(viewModel.uiState.value.showOverlay)
    }

    @Test
    fun `restored page opens without animation`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.setViewportSize(viewWidth, viewHeight)
        viewModel.openChapter(File("dir"), initialPageIndex = 1)
        val state = withTimeout(STATE_TIMEOUT_MS) { viewModel.uiState.first { it.chapter != null } }
        assertEquals(1, state.currentPageIndex)
        assertEquals(1, state.scrollTarget)
        assertFalse(state.scrollTargetAnimated)
        assertNull(state.scrollStep)
    }

    @Test
    fun `translation toggle is available only with overlay specs`() {
        assertFalse(chapter().hasOverlays)
        val spec = OverlaySpec(
            bubbleId = "b1",
            pageIndex = 0,
            lines = listOf(OverlayLine(text = "Привет", baselineStart = PointF(x = 10f, y = 30f), widthPx = 60f)),
            sizePx = 20f,
            lineSpacingMult = 1f,
            letterSpacing = 0f,
            scaleX = 1f,
            colorArgb = 0xFF000000.toInt(),
        )
        val translated = chapter().let { base ->
            base.copy(pages = base.pages.mapIndexed { index, page -> if (index == 0) page.copy(overlays = listOf(spec)) else page })
        }
        assertTrue(translated.hasOverlays)
    }

    @Test
    fun `bubble tap does not change chrome`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.onTap(pageIndex = 0, x = 100f, y = 100f)
        assertEquals("b1", viewModel.uiState.value.selectedBubble?.bubbleId)
        assertTrue(viewModel.uiState.value.chromeVisible)
    }

    @Test
    fun `miss tap with open sheet only closes it and keeps chrome`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.onTap(pageIndex = 0, x = 100f, y = 100f)
        viewModel.onTap(pageIndex = 0, x = 900f, y = 2000f)
        assertNull(viewModel.uiState.value.selectedBubble)
        // Первый промах закрыл лист, chrome не тронут.
        assertTrue(viewModel.uiState.value.chromeVisible)
    }

    @Test
    fun `double tap zooms to 2_5x and back to base`() = runTest {
        val viewModel = viewModelReturning(chapter())
        val state = viewModel.openedChapter()
        val base = state.baseScaleFor(0)
        viewModel.onDoubleTapZoom(pageIndex = 0, focusX = viewWidth / 2f, focusY = 200f)
        assertEquals(base * DOUBLE_TAP_ZOOM, viewModel.uiState.value.transformFor(0).scale, EPS)
        viewModel.onDoubleTapZoom(pageIndex = 0, focusX = viewWidth / 2f, focusY = 200f)
        assertEquals(base, viewModel.uiState.value.transformFor(0).scale, EPS)
    }

    @Test
    fun `scroll target set and consumed`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.requestScrollToPage(1)
        assertEquals(1, viewModel.uiState.value.scrollTarget)
        viewModel.consumeScrollTarget()
        assertNull(viewModel.uiState.value.scrollTarget)
    }

    @Test
    fun `current page coerced into range`() = runTest {
        val viewModel = viewModelReturning(chapter())
        viewModel.openedChapter()
        viewModel.setCurrentPage(99)
        assertEquals(1, viewModel.uiState.value.currentPageIndex)
        viewModel.setCurrentPage(-5)
        assertEquals(0, viewModel.uiState.value.currentPageIndex)
    }

    private companion object {
        const val EPS = 0.001f
        const val DOUBLE_TAP_ZOOM = 2.5f
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
