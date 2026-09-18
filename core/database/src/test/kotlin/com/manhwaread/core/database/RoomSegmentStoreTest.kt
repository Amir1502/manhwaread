package com.manhwaread.core.database

import com.manhwaread.core.vision.DetectedLang
import com.manhwaread.core.vision.TextSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSegmentStoreTest : DaoTestBase() {
    private fun segment(id: String, order: Int, edited: Boolean = false, translated: String? = null) = TextSegment(
        id = id,
        bubbleId = "b-$id",
        pageIndex = 0,
        ocrText = "text-$id",
        ocrLang = DetectedLang.KO,
        ocrConfidence = 0.9f,
        readingOrder = order,
        translatedText = translated,
        isEditedByUser = edited,
    )

    @Test
    fun `save and load roundtrip preserves fields`() = runBlocking {
        val store = RoomSegmentStore(db.segmentDao())
        val segments = listOf(
            segment("s1", order = 1, edited = true, translated = "привет"),
            segment("s2", order = 0),
        )
        store.saveSegments(chapterId = 10L, segments = segments)
        // Загрузка упорядочена по readingOrder (контракт SegmentDao).
        val loaded = store.loadSegments(10L)
        assertEquals(listOf("s2", "s1"), loaded.map { it.id })
        val first = loaded.first { it.id == "s1" }
        assertEquals("привет", first.translatedText)
        assertTrue(first.isEditedByUser)
        assertEquals(DetectedLang.KO, first.ocrLang)
        assertEquals(0.9f, first.ocrConfidence)
    }

    @Test
    fun `save replaces previous chapter segments`() = runBlocking {
        val store = RoomSegmentStore(db.segmentDao())
        store.saveSegments(10L, listOf(segment("old", order = 0)))
        store.saveSegments(10L, listOf(segment("new", order = 0)))
        assertEquals(listOf("new"), store.loadSegments(10L).map { it.id })
    }

    @Test
    fun `load for unknown chapter is empty`() = runBlocking {
        val store = RoomSegmentStore(db.segmentDao())
        assertTrue(store.loadSegments(404L).isEmpty())
    }
}
