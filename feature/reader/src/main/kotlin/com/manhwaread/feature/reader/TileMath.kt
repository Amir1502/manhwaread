package com.manhwaread.feature.reader

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Математика тайлов для TiledImageView: какие участки изображения нужно
// декодировать под текущий вьюпорт. Чистые функции — тестируются на JVM.
// DoD «800×20000 без OOM» обеспечивается тем, что декодируются только
// видимые тайлы, а их число ограничено площадью вьюпорта.
// TileRect объявлен в ReaderModels.kt (общие модели читалки).

// Размер стороны тайла по умолчанию.
const val DEFAULT_TILE_SIZE = 512

// Полное число тайлов сетки (для оценки общего объёма изображения).
fun tileCount(imageWidthPx: Int, imageHeightPx: Int, tileSize: Int = DEFAULT_TILE_SIZE): Int {
    val cols = (imageWidthPx + tileSize - 1) / tileSize
    val rows = (imageHeightPx + tileSize - 1) / tileSize
    return cols * rows
}

// Видимый прямоугольник изображения в его пикселях; null, если вьюпорт вне изображения.
// Делегирует полосовой версии: вся математика живёт в visibleImageRectForBand.
fun visibleImageRect(
    transform: ViewportTransform,
    viewWidthPx: Float,
    viewHeightPx: Float,
    imageWidthPx: Int,
    imageHeightPx: Int,
): TileRect? = visibleImageRectForBand(
    transform = transform,
    bandLeftPx = 0f,
    bandTopPx = 0f,
    bandRightPx = viewWidthPx,
    bandBottomPx = viewHeightPx,
    imageWidthPx = imageWidthPx,
    imageHeightPx = imageHeightPx,
)

// Видимый прямоугольник изображения для произвольной полосы вью в её
// экранных координатах; null, если полоса не пересекает изображение.
// В вебтуне вью страницы ростом со всю полосу (например 800×20000), но
// реально на экране видна лишь полоса LazyColumn — обрезка тайловой
// математики по фактически видимой полосе исключает декодирование всей
// страницы и выбивание LRU-кэша (чёрные дыры на длинных страницах).
fun visibleImageRectForBand(
    transform: ViewportTransform,
    bandLeftPx: Float,
    bandTopPx: Float,
    bandRightPx: Float,
    bandBottomPx: Float,
    imageWidthPx: Int,
    imageHeightPx: Int,
): TileRect? {
    val left = floor(transform.toImageX(bandLeftPx).toDouble()).toInt()
    val top = floor(transform.toImageY(bandTopPx).toDouble()).toInt()
    val right = transform.toImageX(bandRightPx).toInt() + 1
    val bottom = transform.toImageY(bandBottomPx).toInt() + 1
    val clampedLeft = left.coerceIn(0, imageWidthPx)
    val clampedTop = top.coerceIn(0, imageHeightPx)
    val clampedRight = right.coerceIn(0, imageWidthPx)
    val clampedBottom = bottom.coerceIn(0, imageHeightPx)
    if (clampedLeft >= clampedRight || clampedTop >= clampedBottom) {
        return null
    }
    return TileRect(clampedLeft, clampedTop, clampedRight, clampedBottom)
}

// Тайлы сетки, пересекающие видимый прямоугольник (краевые обрезаны по изображению).
fun visibleTiles(
    visible: TileRect,
    imageWidthPx: Int,
    imageHeightPx: Int,
    tileSize: Int = DEFAULT_TILE_SIZE,
): List<TileRect> {
    val firstCol = visible.left / tileSize
    val lastCol = (visible.right - 1) / tileSize
    val firstRow = visible.top / tileSize
    val lastRow = (visible.bottom - 1) / tileSize
    val tiles = mutableListOf<TileRect>()
    for (row in firstRow..lastRow) {
        for (col in firstCol..lastCol) {
            tiles += TileRect(
                left = col * tileSize,
                top = row * tileSize,
                right = min((col + 1) * tileSize, imageWidthPx),
                bottom = min((row + 1) * tileSize, imageHeightPx),
            )
        }
    }
    return tiles
}

// Шаг прореживания декодирования по АБСОЛЮТНОМУ экранному масштабу:
// наибольшая степень двойки sample, при которой sample * 2 * absoluteScale > 1.
// Страница, показанная уменьшенной (scale ~ 0.5), декодируется в 1/2 или
// 1/4 разрешения — экономия памяти и времени декодирования вместо
// полномерного растра. Прежняя версия получала относительный зум
// (transform.scale / baseScale, всегда >= 1 из-за клампа) и поэтому
// никогда не прореживала декодирование.
fun sampleSizeForScale(absoluteScale: Float): Int {
    // Нулевой/отрицательный масштаб недопустим: без защиты цикл ниже бесконечен.
    if (absoluteScale <= 0f) {
        return 1
    }
    var sample = 1
    while (sample * 2 * absoluteScale <= 1f) {
        sample *= 2
    }
    return sample
}

// Верхняя оценка пикселей, декодируемых за один кадр (для OOM-гарантии):
// видимая площадь, приведённая к пикселям изображения, с запасом на шаг тайла.
fun maxDecodedPixelsPerFrame(
    viewWidthPx: Float,
    viewHeightPx: Float,
    scale: Float,
    tileSize: Int = DEFAULT_TILE_SIZE,
): Long {
    val visibleWidth = viewWidthPx / scale + tileSize
    val visibleHeight = viewHeightPx / scale + tileSize
    return max(1L, visibleWidth.toLong() * visibleHeight.toLong())
}
