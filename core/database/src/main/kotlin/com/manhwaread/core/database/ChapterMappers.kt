package com.manhwaread.core.database

import com.manhwaread.core.model.Category
import com.manhwaread.core.model.Chapter

// Маппинг глав и категорий; Category.order хранится в колонке sortOrder ("order" — слово SQL).

internal fun ChapterEntity.toDomain(): Chapter =
    Chapter(id, mangaId, url, name, season, chapterNumber, dateUploadMs, scanlator, read)

internal fun Chapter.toEntity(): ChapterEntity =
    ChapterEntity(id, mangaId, url, name, season, chapterNumber, dateUploadMs, scanlator, read)

internal fun CategoryEntity.toDomain(): Category = Category(id, name, sortOrder, isSystem)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(id, name, order, isSystem)
