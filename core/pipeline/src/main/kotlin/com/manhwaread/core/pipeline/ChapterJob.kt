package com.manhwaread.core.pipeline

import com.manhwaread.core.common.AppError

/** Идентификация главы для конвейера: источник, тайтл, глава, URL. */
data class ChapterRef(
    val sourceId: Long,
    val mangaId: Long,
    val chapterId: Long,
    val chapterUrl: String,
)

/** Изменяемая часть задачи: стадия, число попыток, последняя ошибка. */
data class JobState(
    val status: StageStatus = StageStatus.QUEUED,
    val attempts: Int = 0,
    val lastError: AppError? = null,
)

/**
 * Задача перевода главы — единица очереди (ChapterJob из AGENTS.md).
 * Приоритет выше — раньше в работе; при равенстве — FIFO по [createdAt].
 */
data class ChapterJob(
    val id: String,
    val ref: ChapterRef,
    val priority: Int = 0,
    val createdAt: Long = 0L,
    val state: JobState = JobState(),
)
