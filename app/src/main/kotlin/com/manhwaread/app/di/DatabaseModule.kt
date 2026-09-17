package com.manhwaread.app.di

import android.content.Context
import androidx.room.Room
import com.manhwaread.core.database.BookmarkDao
import com.manhwaread.core.database.CategoryDao
import com.manhwaread.core.database.ChapterDao
import com.manhwaread.core.database.DownloadTaskDao
import com.manhwaread.core.database.HistoryDao
import com.manhwaread.core.database.MangaDao
import com.manhwaread.core.database.ManhwareadDatabase
import com.manhwaread.core.database.SegmentDao
import com.manhwaread.core.database.TranslationJobDao
import com.manhwaread.core.database.allMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-модуль БД: синглтон Room + провайдеры всех DAO. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    // Spread — единственный способ передать массив в vararg addMigrations;
    // выполняется один раз при старте приложения, массив миграций мал.
    @Suppress("SpreadOperator")
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ManhwareadDatabase =
        Room.databaseBuilder(context, ManhwareadDatabase::class.java, ManhwareadDatabase.NAME)
            .addMigrations(*allMigrations)
            .build()

    @Provides
    fun provideMangaDao(database: ManhwareadDatabase): MangaDao = database.mangaDao()

    @Provides
    fun provideChapterDao(database: ManhwareadDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideCategoryDao(database: ManhwareadDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideHistoryDao(database: ManhwareadDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideBookmarkDao(database: ManhwareadDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideDownloadTaskDao(database: ManhwareadDatabase): DownloadTaskDao = database.downloadTaskDao()

    @Provides
    fun provideTranslationJobDao(database: ManhwareadDatabase): TranslationJobDao = database.translationJobDao()

    @Provides
    fun provideSegmentDao(database: ManhwareadDatabase): SegmentDao = database.segmentDao()
}
