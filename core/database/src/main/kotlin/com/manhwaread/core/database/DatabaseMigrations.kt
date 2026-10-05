package com.manhwaread.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v2 — русский тайтл в библиотеке: добавляем nullable-колонку titleRu в таблицу "manga".
// Массив allMigrations подключается к билдеру БД через addMigrations(*allMigrations)
// (DI :app); каждая миграция покрывается тестом в DatabaseMigrationsTest.
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN titleRu TEXT")
    }
}

// v3 — метаданные тайтла и статус чтения: добавляем nullable-колонки в таблицу "manga".
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE manga ADD COLUMN readingStatus TEXT")
        db.execSQL("ALTER TABLE manga ADD COLUMN rating REAL")
        db.execSQL("ALTER TABLE manga ADD COLUMN altTitle TEXT")
        db.execSQL("ALTER TABLE manga ADD COLUMN type TEXT")
        db.execSQL("ALTER TABLE manga ADD COLUMN ageRating TEXT")
        db.execSQL("ALTER TABLE manga ADD COLUMN year INTEGER")
        db.execSQL("ALTER TABLE manga ADD COLUMN chapterCount INTEGER")
    }
}

val allMigrations: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
