package com.manhwaread.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Главная БД: библиотека, главы, категории, история, закладки, загрузки, очередь, сегменты. */
@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryCrossRef::class,
        HistoryEntity::class,
        BookmarkEntity::class,
        DownloadTaskEntity::class,
        TranslationJobEntity::class,
        SegmentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ManhwareadDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun translationJobDao(): TranslationJobDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        const val NAME = "manhwaread.db"
    }
}
