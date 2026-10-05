package com.manhwaread.core.database

import com.manhwaread.core.model.DownloadStatus
import com.manhwaread.core.pipeline.StageStatus
import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.source.api.MangaStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `genres roundtrip`() {
        val genres = listOf("action", "drama", "with \"quotes\" and \n newline")
        assertEquals(genres, converters.stringToGenres(converters.genresToString(genres)))
    }

    @Test
    fun `empty genres roundtrip`() {
        val encoded = converters.genresToString(emptyList())
        assertEquals("[]", encoded)
        assertEquals(emptyList<String>(), converters.stringToGenres(encoded))
    }

    @Test
    fun `manga status roundtrip and fallback`() {
        MangaStatus.entries.forEach {
            assertEquals(it, converters.stringToMangaStatus(converters.mangaStatusToString(it)))
        }
        assertEquals(MangaStatus.UNKNOWN, converters.stringToMangaStatus("SOMETHING_NEW"))
    }

    @Test
    fun `download status roundtrip and fallback`() {
        DownloadStatus.entries.forEach {
            assertEquals(it, converters.stringToDownloadStatus(converters.downloadStatusToString(it)))
        }
        assertEquals(DownloadStatus.PENDING, converters.stringToDownloadStatus("???"))
    }

    @Test
    fun `stage status roundtrip and fallback`() {
        StageStatus.entries.forEach {
            assertEquals(it, converters.stringToStageStatus(converters.stageStatusToString(it)))
        }
        assertEquals(StageStatus.QUEUED, converters.stringToStageStatus("???"))
    }

    @Test
    fun `detected lang roundtrip and fallback`() {
        DetectedLang.entries.forEach {
            assertEquals(it, converters.stringToDetectedLang(converters.detectedLangToString(it)))
        }
        assertEquals(DetectedLang.UNKNOWN, converters.stringToDetectedLang("???"))
    }

    @Test
    fun `reading status roundtrip and fallback`() {
        com.manhwaread.core.model.ReadingStatus.entries.forEach {
            assertEquals(it, converters.stringToReadingStatus(converters.readingStatusToString(it)))
        }
        assertEquals(null, converters.stringToReadingStatus(null))
        assertEquals(null, converters.stringToReadingStatus("UNKNOWN_STATUS"))
        assertEquals(null, converters.readingStatusToString(null))
    }

    @Test
    fun `manga type roundtrip and fallback`() {
        com.manhwaread.source.api.MangaType.entries.forEach {
            assertEquals(it, converters.stringToMangaType(converters.mangaTypeToString(it)))
        }
        assertEquals(null, converters.stringToMangaType(null))
        assertEquals(null, converters.stringToMangaType("UNKNOWN_TYPE"))
        assertEquals(null, converters.mangaTypeToString(null))
    }
}
