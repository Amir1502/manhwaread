package com.manhwaread.feature.downloads.vision

import kotlin.math.exp

// Чистая математика препроцессинга/постпроцессинга ONNX-сегментации:
// тестируется на JVM без нативных библиотек.

// Максимальное значение цветового канала (8 бит).
private const val COLOR_SCALE = 255f

// Нормализация канала: (x/255 - mean) / std — стандарт ImageNet-подобных моделей.
fun normalizeChannel(value: Float, mean: Float, std: Float): Float {
    require(std != 0f) { "std must be non-zero" }
    return (value / COLOR_SCALE - mean) / std
}

// Упаковка ARGB-пикселей (HWC, порядок в IntArray) в планарный NCHW-тензор
// [1, 3, height, width]: сначала план красного канала, затем зелёного, синего.
fun toNchw(argb: IntArray, width: Int, height: Int, mean: Float, std: Float): FloatArray {
    require(width > 0 && height > 0) { "width/height must be positive" }
    require(argb.size == width * height) { "pixel count must match width*height" }
    val planeSize = width * height
    val output = FloatArray(planeSize * CHANNELS_RGB)
    for (index in argb.indices) {
        val pixel = argb[index]
        output[index] = normalizeChannel(((pixel shr RED_SHIFT) and CHANNEL_MASK).toFloat(), mean, std)
        output[planeSize + index] = normalizeChannel(((pixel shr GREEN_SHIFT) and CHANNEL_MASK).toFloat(), mean, std)
        output[planeSize * 2 + index] = normalizeChannel((pixel and CHANNEL_MASK).toFloat(), mean, std)
    }
    return output
}

// Сигмоида логита маски.
fun sigmoid(logit: Float): Float = 1f / (1f + exp(-logit))

// Декодирование выхода сегментации (логиты [H*W]) в булеву маску.
fun decodeMask(logits: FloatArray, width: Int, height: Int, threshold: Float): BooleanArray {
    require(width > 0 && height > 0) { "width/height must be positive" }
    require(logits.size == width * height) { "logit count must match width*height" }
    return BooleanArray(logits.size) { index -> sigmoid(logits[index]) >= threshold }
}

private const val CHANNELS_RGB = 3
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
