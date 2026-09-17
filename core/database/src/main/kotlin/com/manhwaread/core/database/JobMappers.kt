package com.manhwaread.core.database

import com.manhwaread.core.common.AppError
import com.manhwaread.core.pipeline.ChapterJob
import com.manhwaread.core.pipeline.ChapterRef
import com.manhwaread.core.pipeline.JobState
import com.manhwaread.core.vision.TextSegment

// Маппинг очереди конвейера и OCR-сегментов.

internal fun appErrorToString(error: AppError?): String? = error?.toString()

// Обратное отображение с потерей типа: в БД хранится текст описания ошибки,
// восстанавливаем её как AppError.Unknown с исходным описанием в cause.
internal fun stringToAppError(raw: String?): AppError? = raw?.let { AppError.Unknown(IllegalStateException(it)) }

internal fun ChapterJob.toEntity(): TranslationJobEntity =
    TranslationJobEntity(
        id = id,
        sourceId = ref.sourceId,
        mangaId = ref.mangaId,
        chapterId = ref.chapterId,
        chapterUrl = ref.chapterUrl,
        priority = priority,
        createdAt = createdAt,
        status = state.status,
        attempts = state.attempts,
        lastError = appErrorToString(state.lastError),
    )

internal fun TranslationJobEntity.toDomain(): ChapterJob =
    ChapterJob(
        id = id,
        ref = ChapterRef(sourceId, mangaId, chapterId, chapterUrl),
        priority = priority,
        createdAt = createdAt,
        state = JobState(status, attempts, stringToAppError(lastError)),
    )

internal fun SegmentEntity.toDomain(): TextSegment =
    TextSegment(
        id = id,
        bubbleId = bubbleId,
        pageIndex = pageIndex,
        ocrText = ocrText,
        ocrLang = ocrLang,
        ocrConfidence = ocrConfidence,
        readingOrder = readingOrder,
        isSfx = isSfx,
        translatedText = translatedText,
        isEditedByUser = isEditedByUser,
        needsRetry = needsRetry,
    )

internal fun TextSegment.toEntity(chapterId: Long): SegmentEntity =
    SegmentEntity(
        id = id,
        chapterId = chapterId,
        bubbleId = bubbleId,
        pageIndex = pageIndex,
        ocrText = ocrText,
        ocrLang = ocrLang,
        ocrConfidence = ocrConfidence,
        readingOrder = readingOrder,
        isSfx = isSfx,
        translatedText = translatedText,
        isEditedByUser = isEditedByUser,
        needsRetry = needsRetry,
    )
