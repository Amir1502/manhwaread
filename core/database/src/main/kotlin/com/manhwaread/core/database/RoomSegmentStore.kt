package com.manhwaread.core.database

import com.manhwaread.core.pipeline.SegmentStore
import com.manhwaread.core.vision.TextSegment

// Персистентное хранилище OCR-сегментов конвейера (ФАЗА 15): полная замена
// набора сегментов главы, ручные правки защищаются флагом isEditedByUser
// на уровне SegmentDao/переводчика.
class RoomSegmentStore(private val segmentDao: SegmentDao) : SegmentStore {
    override suspend fun saveSegments(chapterId: Long, segments: List<TextSegment>) {
        segmentDao.replaceForChapter(chapterId, segments.map { segment -> segment.toEntity(chapterId) })
    }

    override suspend fun loadSegments(chapterId: Long): List<TextSegment> =
        segmentDao.allForChapter(chapterId).map { entity -> entity.toDomain() }
}
