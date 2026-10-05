package com.manhwaread.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Проверка MIGRATION_1_2 на реальной SQLite: таблица "manga" воссоздаётся
// по схеме v1 (срез из schemas/1.json), миграция выполняется вручную, затем
// контролируются новая колонка и сохранность строк.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationsTest {
    private var helper: SupportSQLiteOpenHelper? = null

    @After
    fun closeDatabase() {
        helper?.close()
    }

    @Test
    fun `migration 1 to 2 adds nullable titleRu keeping rows`() {
        val db = openV1Database()
        db.execSQL(
            "INSERT INTO manga (sourceId, url, title, genres, status, nsfw, inLibrary, addedAtMs) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(1L, "/manga/solo-leveling", "Solo Leveling", """["action"]""", "ONGOING", 0, 1, 7L),
        )

        MIGRATION_1_2.migrate(db)

        val titleRu = tableColumns(db)["titleRu"]
        assertNotNull("titleRu column missing after migration", titleRu)
        assertEquals("TEXT", titleRu?.first)
        assertFalse("titleRu must stay nullable", titleRu?.second ?: true)
        db.query("SELECT title, titleRu FROM manga").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Solo Leveling", cursor.getString(0))
            assertTrue("existing rows must get NULL titleRu", cursor.isNull(1))
        }
    }

    @Test
    fun `migration 2 to 3 adds nullable metadata columns keeping rows`() {
        val db = openV2Database()
        db.execSQL(
            "INSERT INTO manga (sourceId, url, title, genres, status, nsfw, inLibrary, addedAtMs, titleRu) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(1L, "/manga/solo-leveling", "Solo Leveling", """["action"]""", "ONGOING", 0, 1, 7L, "Поднятие"),
        )

        MIGRATION_2_3.migrate(db)

        val cols = tableColumns(db)
        val expectedCols = mapOf(
            "readingStatus" to "TEXT",
            "rating" to "REAL",
            "altTitle" to "TEXT",
            "type" to "TEXT",
            "ageRating" to "TEXT",
            "year" to "INTEGER",
            "chapterCount" to "INTEGER",
        )

        for ((name, expectedType) in expectedCols) {
            val col = cols[name]
            assertNotNull("$name column missing after migration 2->3", col)
            assertEquals("$name should have type $expectedType", expectedType, col?.first)
            assertFalse("$name must stay nullable", col?.second ?: true)
        }

        db.query("SELECT title, titleRu, readingStatus, rating, type, year FROM manga").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Solo Leveling", cursor.getString(0))
            assertEquals("Поднятие", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertTrue(cursor.isNull(5))
        }
    }

    @Test
    fun `sequential migration 1 to 2 to 3 preserves data`() {
        val db = openV1Database()
        db.execSQL(
            "INSERT INTO manga (sourceId, url, title, genres, status, nsfw, inLibrary, addedAtMs) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(1L, "/manga/solo", "Solo", """["action"]""", "ONGOING", 0, 1, 10L),
        )

        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)

        db.query("SELECT title, titleRu, readingStatus FROM manga").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Solo", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    fun `migration registry exposes versions for room builder`() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
        assertEquals(2, MIGRATION_2_3.startVersion)
        assertEquals(3, MIGRATION_2_3.endVersion)
        assertTrue(allMigrations.contains(MIGRATION_1_2))
        assertTrue(allMigrations.contains(MIGRATION_2_3))
    }

    // In-memory БД версии 1: onCreate создаёт таблицу "manga" в точности как в schemas/1.json.
    private fun openV1Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(SCHEMA_VERSION_V1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV1MangaTable(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper = openHelper
        return openHelper.writableDatabase
    }

    private fun createV1MangaTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `manga` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sourceId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`artist` TEXT, `author` TEXT, `description` TEXT, `genres` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `thumbnailUrl` TEXT, `nsfw` INTEGER NOT NULL, " +
                "`inLibrary` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_sourceId_url` ON `manga` (`sourceId`, `url`)")
    }

    // In-memory БД версии 2: onCreate создаёт таблицу "manga" в точности как в schemas/2.json.
    private fun openV2Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(SCHEMA_VERSION_V2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV2MangaTable(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper = openHelper
        return openHelper.writableDatabase
    }

    private fun createV2MangaTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `manga` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sourceId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`artist` TEXT, `author` TEXT, `description` TEXT, `genres` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `thumbnailUrl` TEXT, `nsfw` INTEGER NOT NULL, " +
                "`inLibrary` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL, `titleRu` TEXT)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_sourceId_url` ON `manga` (`sourceId`, `url`)")
    }

    // PRAGMA table_info → имя колонки → (тип SQLite, признак NOT NULL).
    private fun tableColumns(db: SupportSQLiteDatabase): Map<String, Pair<String, Boolean>> {
        val columns = mutableMapOf<String, Pair<String, Boolean>>()
        db.query("PRAGMA table_info(`manga`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] = cursor.getString(typeIndex) to (cursor.getInt(notNullIndex) == 1)
            }
        }
        return columns
    }

    private companion object {
        const val SCHEMA_VERSION_V1 = 1
        const val SCHEMA_VERSION_V2 = 2
    }
}
