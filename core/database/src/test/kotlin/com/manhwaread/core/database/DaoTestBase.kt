package com.manhwaread.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Таймаут ожидания первой эмиссии Flow в тестах (мс).
internal const val DAO_TIMEOUT_MS = 5_000L

// База Robolectric-тестов DAO: in-memory БД, запросы с главной нити разрешены (runBlocking).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class DaoTestBase {
    protected lateinit var db: ManhwareadDatabase

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ManhwareadDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // Вставляет тайтл с одной главой (FK-целостность для истории/закладок); возвращает (mangaId, chapterId).
    protected suspend fun insertMangaWithChapter(chapterUrl: String = "/ch/1"): Pair<Long, Long> {
        val mangaId = db.mangaDao().upsert(DatabaseFixtures.manga())
        val chapterId = db.chapterDao().insertIgnore(DatabaseFixtures.chapter(mangaId, url = chapterUrl))
        return mangaId to chapterId
    }
}
