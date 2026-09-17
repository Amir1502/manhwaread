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
}
