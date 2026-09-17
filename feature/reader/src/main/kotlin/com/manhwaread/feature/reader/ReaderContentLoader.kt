package com.manhwaread.feature.reader

import java.io.File

// Читатель размеров изображения: отделён от BitmapFactory, чтобы
// загрузчик главы можно было тестировать на чистой JVM.
interface ImageSizeReader {
    fun read(file: File): Pair<Int, Int>
}

// Загрузчик контента главы для читалки (офлайн-путь: каталог на диске).
interface ReaderContentLoader {
    suspend fun loadChapter(chapterDir: File): ReaderChapter
}
