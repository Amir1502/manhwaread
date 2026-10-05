package com.manhwaread.feature.details

import com.manhwaread.core.database.ChapterEntity
import com.manhwaread.core.database.MangaEntity
import com.manhwaread.core.model.Chapter
import com.manhwaread.core.model.ChapterNumberParser
import com.manhwaread.source.api.SChapter
import com.manhwaread.source.api.SManga

// Маппинг доменных моделей источника в Room-сущности карточки (ФАЗА 14).

/**
 * SManga → MangaEntity с сохранением библиотечного состояния: upsertBySourceUrl
 * заменяет строку целиком, поэтому id/inLibrary/addedAtMs/titleRu берутся из
 * существующей (русский тайтл накапливается локально — источник о нём не знает).
 */
internal fun SManga.toEntityPreserving(existing: MangaEntity?): MangaEntity = MangaEntity(
    id = existing?.id ?: 0L,
    sourceId = sourceId,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genres = genres,
    status = status,
    thumbnailUrl = thumbnailUrl,
    nsfw = nsfw,
    inLibrary = existing?.inLibrary ?: false,
    addedAtMs = existing?.addedAtMs ?: 0L,
    titleRu = existing?.titleRu,
    readingStatus = existing?.readingStatus,
    rating = rating ?: existing?.rating,
    altTitle = altTitle ?: existing?.altTitle,
    type = type ?: existing?.type,
    ageRating = ageRating ?: existing?.ageRating,
    year = year ?: existing?.year,
    chapterCount = chapterCount ?: existing?.chapterCount,
)

/** SChapter → ChapterEntity: сезон из названия, номер — из источника или парсера. */
internal fun SChapter.toEntity(mangaId: Long): ChapterEntity = ChapterEntity(
    mangaId = mangaId,
    url = url,
    name = name,
    season = ChapterNumberParser.parseSeason(name),
    chapterNumber = if (chapterNumber >= 0f) chapterNumber else ChapterNumberParser.parse(name),
    dateUploadMs = dateUpload,
    scanlator = scanlator,
    read = false,
)

/** ChapterEntity → Chapter (доменная модель для работы с NextChapterResolver). */
internal fun ChapterEntity.toDomain(): Chapter = Chapter(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    season = season,
    chapterNumber = chapterNumber,
    dateUploadMs = dateUploadMs,
    scanlator = scanlator,
    read = read,
)

/** Chapter → ChapterEntity. */
internal fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    mangaId = mangaId,
    url = url,
    name = name,
    season = season,
    chapterNumber = chapterNumber,
    dateUploadMs = dateUploadMs,
    scanlator = scanlator,
    read = read,
)
