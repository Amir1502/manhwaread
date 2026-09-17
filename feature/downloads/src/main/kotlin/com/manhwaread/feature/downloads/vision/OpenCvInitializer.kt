package com.manhwaread.feature.downloads.vision

import org.opencv.android.OpenCVLoader

// Инициализация нативной библиотеки OpenCV из Maven-AAR (без OpenCV Manager).
object OpenCvInitializer {
    @Volatile
    private var loaded = false

    // Повторные вызовы дешевы: флаг кэширует успешную загрузку.
    fun ensureLoaded(): Boolean {
        if (loaded) {
            return true
        }
        // VERIFY-API: OpenCVLoader.initLocal() доступен в org.opencv:opencv 4.9.0+
        // (Maven-AAR); при провале возвращает false вместо исключения.
        loaded = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        return loaded
    }
}
