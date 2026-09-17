package com.manhwaread.core.database

import androidx.room.migration.Migration

// v1 — исходная схема: миграций пока нет. Схема экспортирована в schemas/1.json;
// начиная с v2 каждая миграция добавляется в этот массив и покрывается тестом
// через MigrationTestHelper (:room-testing).
val allMigrations: Array<Migration> = emptyArray()
