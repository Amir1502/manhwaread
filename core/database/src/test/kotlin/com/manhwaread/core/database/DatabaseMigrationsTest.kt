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
    fun `migration registry exposes versions for room builder`() {
        assertEquals(1, MIGRATION_1_2.startVersion)
        assertEquals(2, MIGRATION_1_2.endVersion)
        assertTrue(allMigrations.contains(MIGRATION_1_2))
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
    }
}
