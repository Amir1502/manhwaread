package com.manhwaread.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView

// Порог «страница увеличена»: масштаб больше базового хотя бы на процент.
private const val ZOOMED_EPSILON = 1.01f

// Цвет заглушки недостроенной/битой страницы: тот же нейтральный серый,
// что и плейсхолдер тайла в TiledImageView — никаких чёрных дыр.
private val PagePlaceholderColor = Color(0xFF2C2F36)

// Одна страница читалки: тайловый растр + векторный слой перевода поверх.
// Жесты: пинч-зум до 5x, панорамирование в зуме, тап — карточка бабла,
// двойной тап — быстрый зум 2.5x/сброс. Ключи pointerInput не содержат
// трансформацию — иначе жест прерывался бы на каждом кадре зума.
// Битая страница (isCorrupted) и жёсткая ошибка декодирования показывают
// серую заглушку с текстом; ошибка дополнительно даёт кнопку «Повторить»,
// пересоздающую тайловую вью.
@Composable
fun ReaderPageView(
    page: ReaderPage,
    transform: ViewportTransform,
    baseScale: Float,
    showOverlay: Boolean,
    actions: ReaderPageActions,
    modifier: Modifier = Modifier,
) {
    if (page.isCorrupted) {
        // Файл не читается вообще: тайловую вью не создаём, трансформация
        // для нулевых размеров не имеет смысла.
        PagePlaceholderBox(text = stringResource(R.string.reader_page_corrupted))
        return
    }
    var pageFailed by remember(page.index) { mutableStateOf(false) }
    var retryKey by remember(page.index) { mutableIntStateOf(0) }
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
        // key(retryKey): «Повторить» пересоздаёт тайловую вью — свежий
        // декодер и сброшенный кэш вместо навсегда чёрной страницы.
        key(retryKey) {
            AndroidView(
                factory = { context ->
                    TiledImageView(context).apply {
                        // Колбэк приходит на главный поток (post из вью).
                        onDecodeError = { pageFailed = true }
                    }
                },
                update = { view ->
                    view.setImage(page.imageFile, page.widthPx, page.heightPx)
                    view.setViewport(transform, baseScale)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AndroidView(
            factory = { context -> OverlayLayerView(context) },
            update = { view ->
                view.setBackgrounds(projectedBackgrounds)
                view.setOverlayLines(projectedLines)
                view.setOverlayVisible(showOverlay)
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (pageFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PagePlaceholderColor),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.reader_page_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    TextButton(
                        onClick = {
                            pageFailed = false
                            retryKey++
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.reader_page_retry),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// Полноразмерная заглушка страницы: серый фон и центрированный текст.
@Composable
private fun PagePlaceholderBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PagePlaceholderColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}
