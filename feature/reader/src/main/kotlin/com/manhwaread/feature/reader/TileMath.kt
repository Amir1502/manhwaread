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
fun visibleImageRect(
    transform: ViewportTransform,
    viewWidthPx: Float,
    viewHeightPx: Float,
    imageWidthPx: Int,
    imageHeightPx: Int,
): TileRect? {
    val left = floor(transform.toImageX(0f).toDouble()).toInt()
    val top = floor(transform.toImageY(0f).toDouble()).toInt()
    val right = transform.toImageX(viewWidthPx).toInt() + 1
    val bottom = transform.toImageY(viewHeightPx).toInt() + 1
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

// Шаг прореживания декодирования: при относительном зуме < 1 декодируем
// разреженнее, объём декодирования остаётся ограниченным вьюпортом.
fun sampleSizeFor(relativeZoom: Float): Int {
    if (relativeZoom >= 1f) {
        return 1
    }
    var sample = 1
    while (sample * 2 * relativeZoom <= 1f) {
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
