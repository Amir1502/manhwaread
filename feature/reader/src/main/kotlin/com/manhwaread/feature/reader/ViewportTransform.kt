package com.manhwaread.feature.reader

// Трансформация страницы: scale и offset изображения в экранных пикселях.
// Связь координат: screen = image * scale + offset. Чистая математика —
// тестируется на JVM без Android.
data class ViewportTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun toScreenX(imageX: Float): Float = imageX * scale + offsetX

    fun toScreenY(imageY: Float): Float = imageY * scale + offsetY

    fun toImageX(screenX: Float): Float = (screenX - offsetX) / scale

    fun toImageY(screenY: Float): Float = (screenY - offsetY) / scale

    companion object {
        val IDENTITY = ViewportTransform(scale = 1f, offsetX = 0f, offsetY = 0f)

        // DoD: зум до 5x относительно базового масштаба (fit-to-width).
        const val MAX_ZOOM = 5f
    }
}

// Базовый масштаб: ширина изображения полностью занимает ширину вью.
fun baseScaleFor(imageWidthPx: Int, viewWidthPx: Float): Float = viewWidthPx / imageWidthPx

// Начальная трансформация: fit-to-width, верх изображения у верха вью.
fun fitToWidth(imageWidthPx: Int, viewWidthPx: Float): ViewportTransform =
    ViewportTransform(scale = baseScaleFor(imageWidthPx, viewWidthPx), offsetX = 0f, offsetY = 0f)

// Вписать страницу целиком (пейджерные режимы): масштаб — минимум из
// отношений ширины и высоты, содержимое центрируется.
fun fitScreen(
    imageWidthPx: Int,
    imageHeightPx: Int,
    viewWidthPx: Float,
    viewHeightPx: Float,
): ViewportTransform {
    val scale = minOf(viewWidthPx / imageWidthPx, viewHeightPx / imageHeightPx)
    return ViewportTransform(
        scale = scale,
        offsetX = (viewWidthPx - imageWidthPx * scale) / 2f,
        offsetY = (viewHeightPx - imageHeightPx * scale) / 2f,
    )
}

// Зум вокруг фокуса: точка изображения под фокусом остаётся под фокусом.
// Ограничение: [baseScale, baseScale * MAX_ZOOM].
fun ViewportTransform.zoomAround(
    factor: Float,
    focusScreenX: Float,
    focusScreenY: Float,
    baseScale: Float,
): ViewportTransform {
    val minScale = baseScale
    val maxScale = baseScale * ViewportTransform.MAX_ZOOM
    val newScale = (scale * factor).coerceIn(minScale, maxScale)
    if (newScale == scale) {
        return this
    }
    val imageX = toImageX(focusScreenX)
    val imageY = toImageY(focusScreenY)
    return ViewportTransform(
        scale = newScale,
        offsetX = focusScreenX - imageX * newScale,
        offsetY = focusScreenY - imageY * newScale,
    )
}

// Сдвиг на экранные дельты (панорамирование).
fun ViewportTransform.panBy(deltaX: Float, deltaY: Float): ViewportTransform =
    ViewportTransform(scale = scale, offsetX = offsetX + deltaX, offsetY = offsetY + deltaY)

// Ограничение сдвига: изображение не отрывается от краёв вью;
// содержимое меньше вью по оси — центрируется по этой оси.
fun ViewportTransform.clamped(
    imageWidthPx: Int,
    imageHeightPx: Int,
    viewWidthPx: Float,
    viewHeightPx: Float,
): ViewportTransform {
    val contentWidth = imageWidthPx * scale
    val contentHeight = imageHeightPx * scale
    val clampedX = if (contentWidth <= viewWidthPx) {
        (viewWidthPx - contentWidth) / 2f
    } else {
        offsetX.coerceIn(viewWidthPx - contentWidth, 0f)
    }
    val clampedY = if (contentHeight <= viewHeightPx) {
        (viewHeightPx - contentHeight) / 2f
    } else {
        offsetY.coerceIn(viewHeightPx - contentHeight, 0f)
    }
    return ViewportTransform(scale = scale, offsetX = clampedX, offsetY = clampedY)
}
