package com.manhwaread.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView

// Порог «страница увеличена»: масштаб больше базового хотя бы на процент.
private const val ZOOMED_EPSILON = 1.01f

// Одна страница читалки: тайловый растр + векторный слой перевода поверх.
// Жесты: пинч-зум до 5x, панорамирование в зуме, тап — карточка бабла,
// двойной тап — быстрый зум 2.5x/сброс. Ключи pointerInput не содержат
// трансформацию — иначе жест прерывался бы на каждом кадре зума.
@Composable
fun ReaderPageView(
    page: ReaderPage,
    transform: ViewportTransform,
    baseScale: Float,
    showOverlay: Boolean,
    actions: ReaderPageActions,
    modifier: Modifier = Modifier,
) {
    val projectedLines = remember(page.overlays, transform) {
        page.overlays.flatMap { spec -> spec.project(transform) }
    }
    // Маскирующие подложки пересчитываются вместе со строками перевода:
    // фигура бабла обязана совпадать с текстом на любом зуме/сдвиге.
    val projectedBackgrounds = remember(page.overlays, page.bubbles, transform) {
        projectBackgrounds(page.overlays, page.bubbles, transform)
    }
    val isZoomedIn = rememberUpdatedState(transform.scale > baseScale * ZOOMED_EPSILON)
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(page.index) {
                detectTapGestures(
                    onTap = { offset -> actions.onTap(offset.x, offset.y) },
                    onDoubleTap = { offset -> actions.onDoubleTapZoom(offset.x, offset.y) },
                )
            }
            .pointerInput(page.index) {
                zoomAndPanGesture(
                    isZoomedIn = { isZoomedIn.value },
                    onZoom = actions.onZoom,
                    onPan = actions.onPan,
                )
            },
    ) {
        AndroidView(
            factory = { context -> TiledImageView(context) },
            update = { view ->
                view.setImage(page.imageFile, page.widthPx, page.heightPx)
                view.setViewport(transform, baseScale)
            },
            modifier = Modifier.fillMaxSize(),
        )
        AndroidView(
            factory = { context -> OverlayLayerView(context) },
            update = { view ->
                view.setBackgrounds(projectedBackgrounds)
                view.setOverlayLines(projectedLines)
                view.setOverlayVisible(showOverlay)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
