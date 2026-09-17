package com.manhwaread.core.designsystem

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Математика цвета WCAG 2.1: чистые функции над ARGB Int.
// Не зависит от Android — палитра проверяется контраст-тестами на JVM.

private const val CHANNEL_RANGE = 255.0
private const val SRGB_LOW_THRESHOLD = 0.04045
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE_DIVISOR = 1.055
private const val SRGB_GAMMA = 2.4
private const val LUMA_RED = 0.2126
private const val LUMA_GREEN = 0.7152
private const val LUMA_BLUE = 0.0722
private const val CONTRAST_OFFSET = 0.05
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF

/** Порог WCAG AA для обычного текста. */
const val CONTRAST_AA_TEXT = 4.5

/** Порог WCAG для нетекстовых элементов UI (иконки, обводки). */
const val CONTRAST_AA_UI = 3.0

/** sRGB-канал (0..255) в линейную компоненту (0..1). */
fun srgbToLinear(channel: Int): Double {
    val c = channel / CHANNEL_RANGE
    return if (c <= SRGB_LOW_THRESHOLD) c / SRGB_LINEAR_DIVISOR else ((c + SRGB_OFFSET) / SRGB_SCALE_DIVISOR).pow(SRGB_GAMMA)
}

/** Относительная яркость (0..1) ARGB-цвета по WCAG; альфа игнорируется. */
fun relativeLuminance(argb: Int): Double {
    val r = srgbToLinear((argb shr RED_SHIFT) and CHANNEL_MASK)
    val g = srgbToLinear((argb shr GREEN_SHIFT) and CHANNEL_MASK)
    val b = srgbToLinear(argb and CHANNEL_MASK)
    return LUMA_RED * r + LUMA_GREEN * g + LUMA_BLUE * b
}

/** Коэффициент контрастности WCAG (1..21); симметричен относительно аргументов. */
fun contrastRatio(firstArgb: Int, secondArgb: Int): Double {
    val first = relativeLuminance(firstArgb)
    val second = relativeLuminance(secondArgb)
    val lighter = max(first, second)
    val darker = min(first, second)
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}
