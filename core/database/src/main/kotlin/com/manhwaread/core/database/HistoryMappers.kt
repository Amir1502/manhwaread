package com.manhwaread.core.database

import com.manhwaread.core.model.Bookmark
import com.manhwaread.core.model.DownloadTask
import com.manhwaread.core.model.HistoryEntry
import com.manhwaread.core.model.ReadingProgress

// Маппинг истории/закладок/загрузок; ReadingProgress расплющен в две Int-колонки.

internal fun HistoryEntity.toDomain(): HistoryEntry =
    HistoryEntry(mangaId, chapterId, ReadingProgress.of(pageIndex, scrollOffsetPx), lastReadMs)

internal fun HistoryEntry.toEntity(): HistoryEntity =
    HistoryEntity(mangaId, chapterId, progress.pageIndex.value, progress.scrollOffsetPx.value, lastReadMs)

internal fun BookmarkEntity.toDomain(): Bookmark = Bookmark(id, mangaId, chapterId, pageIndex, createdAtMs, note)

internal fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(id, mangaId, chapterId, pageIndex, createdAtMs, note)

internal fun DownloadTaskEntity.toDomain(): DownloadTask =
    DownloadTask(id, mangaId, chapterId, status, progress, enqueuedAtMs)

internal fun DownloadTask.toEntity(): DownloadTaskEntity =
    DownloadTaskEntity(id, mangaId, chapterId, status, progress, enqueuedAtMs)
