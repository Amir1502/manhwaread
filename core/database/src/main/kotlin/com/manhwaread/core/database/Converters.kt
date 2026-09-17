package com.manhwaread.core.database

import androidx.room.TypeConverter
import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.source.api.MangaStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Конвертеры Room: перечисления ↔ имя, список жанров ↔ JSON-массив строк. */
class Converters {
    @TypeConverter
    fun genresToString(genres: List<String>): String =
        buildJsonArray {
            for (genre in genres) {
                add(genre)
            }
        }.toString()

    @TypeConverter
    fun stringToGenres(raw: String): List<String> =
        Json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }

    @TypeConverter
    fun mangaStatusToString(status: MangaStatus): String = status.name

    // Неизвестное имя (старая схема/битые данные) — деградируем в UNKNOWN, не падаем.
    @TypeConverter
    fun stringToMangaStatus(raw: String): MangaStatus =
        MangaStatus.entries.firstOrNull { it.name == raw } ?: MangaStatus.UNKNOWN

    @TypeConverter
    fun downloadStatusToString(status: DownloadStatus): String = status.name

    @TypeConverter
    fun stringToDownloadStatus(raw: String): DownloadStatus =
        DownloadStatus.entries.firstOrNull { it.name == raw } ?: DownloadStatus.PENDING

    @TypeConverter
    fun stageStatusToString(status: StageStatus): String = status.name

    @TypeConverter
    fun stringToStageStatus(raw: String): StageStatus =
        StageStatus.entries.firstOrNull { it.name == raw } ?: StageStatus.QUEUED

    @TypeConverter
    fun detectedLangToString(lang: DetectedLang): String = lang.name

    @TypeConverter
    fun stringToDetectedLang(raw: String): DetectedLang =
        DetectedLang.entries.firstOrNull { it.name == raw } ?: DetectedLang.UNKNOWN
}
