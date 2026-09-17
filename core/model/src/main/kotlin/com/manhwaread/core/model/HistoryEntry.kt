package com.manhwaread.core.model

/**
 * Запись истории чтения: точная позиция в главе и время последнего чтения.
 */
data class HistoryEntry(
    val mangaId: Long,
    val chapterId: Long,
    val progress: ReadingProgress = ReadingProgress.START,
    val lastReadMs: Long = 0L,
)
