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

val allMigrations: Array<Migration> = arrayOf(MIGRATION_1_2)
