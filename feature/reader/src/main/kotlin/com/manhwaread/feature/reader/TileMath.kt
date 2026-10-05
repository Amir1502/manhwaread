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

// Бюджет базового слоя (обзорного растра всей страницы): не более 1 млн px
// (~4 МБ ARGB_8888) и не более 2048 px по стороне — безопасно для лимита
// аппаратной текстуры при отрисовке.
const val BASE_LAYER_MAX_PIXELS = 1_000_000L
const val BASE_LAYER_MAX_SIDE = 2048

// Верхняя граница шага прореживания базового слоя (защита цикла подбора).
const val BASE_LAYER_MAX_SAMPLE_SIZE = 256

// Наибольший шаг прореживания, который ещё ищется среди тайлов-заменителей.
const val MAX_FALLBACK_SAMPLE_SIZE = 64

// Порог «страница недекодируема»: до 3 разных сбойных тайлов, но не больше
// числа тайлов самой страницы — иначе одно-тайловая битая страница
// никогда не сообщила бы об ошибке.
const val TILE_FAILURE_LIMIT = 3

// Шаг прореживания базового слоя: наименьшая степень двойки, при которой
// обзорный растр укладывается в бюджет по пикселям и по стороне.
fun baseLayerSampleSize(imageWidthPx: Int, imageHeightPx: Int): Int {
    if (imageWidthPx <= 0 || imageHeightPx <= 0) {
        return 1
    }
    var sample = 1
    while (sample < BASE_LAYER_MAX_SAMPLE_SIZE) {
        val width = imageWidthPx / sample
        val height = imageHeightPx / sample
        val fits = width <= BASE_LAYER_MAX_SIDE &&
            height <= BASE_LAYER_MAX_SIDE &&
            width.toLong() * height.toLong() <= BASE_LAYER_MAX_PIXELS
        if (fits) {
            break
        }
        sample *= 2
    }
    return sample
}

// Порядок поиска тайла-заменителя того же прямоугольника при смене шага
// прореживания (пинч): сначала более чёткие (target/2 … 1), затем более
// грубые (target*2 … MAX). Целевой шаг в список не входит.
fun fallbackSampleSizes(targetSampleSize: Int): List<Int> {
    val target = targetSampleSize.coerceAtLeast(1)
    val finer = generateSequence(target / 2) { value -> value / 2 }
        .takeWhile { value -> value >= 1 }
        .toList()
    val coarser = generateSequence(target * 2) { value -> value * 2 }
        .takeWhile { value -> value <= MAX_FALLBACK_SAMPLE_SIZE }
        .toList()
    return finer + coarser
}

fun tileFailureLimit(imageWidthPx: Int, imageHeightPx: Int): Int =
    minOf(TILE_FAILURE_LIMIT, max(1, tileCount(imageWidthPx, imageHeightPx)))
