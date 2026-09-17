package com.manhwaread.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentDaoTest : DaoTestBase() {
    @Test
    fun `replace for chapter swaps segments`() = runBlocking {
        val dao = db.segmentDao()
        dao.replaceForChapter(
            chapterId = 100L,
            segments = listOf(
                DatabaseFixtures.segment("s1", chapterId = 100L, readingOrder = 0),
                DatabaseFixtures.segment("s2", chapterId = 100L, readingOrder = 1),
            ),
        )
        dao.replaceForChapter(100L, listOf(DatabaseFixtures.segment("s3", chapterId = 100L, readingOrder = 0)))
        assertEquals(listOf("s3"), dao.allForChapter(100L).map { it.id })
    }

    @Test
    fun `replace keeps other chapters untouched`() = runBlocking {
        val dao = db.segmentDao()
        dao.replaceForChapter(100L, listOf(DatabaseFixtures.segment("s1", chapterId = 100L, readingOrder = 0)))
        dao.replaceForChapter(200L, listOf(DatabaseFixtures.segment("s2", chapterId = 200L, readingOrder = 0)))
        dao.replaceForChapter(200L, listOf(DatabaseFixtures.segment("s3", chapterId = 200L, readingOrder = 0)))
        assertEquals(listOf("s1"), dao.allForChapter(100L).map { it.id })
        assertEquals(listOf("s3"), dao.allForChapter(200L).map { it.id })
    }

    @Test
    fun `observe ordered by reading order`() = runBlocking {
        val dao = db.segmentDao()
        dao.replaceForChapter(
            chapterId = 100L,
            segments = listOf(
                DatabaseFixtures.segment("s2", chapterId = 100L, readingOrder = 2),
                DatabaseFixtures.segment("s1", chapterId = 100L, readingOrder = 1),
            ),
        )
        val ids = withTimeout(DAO_TIMEOUT_MS) { dao.observeForChapter(100L).first() }.map { it.id }
        assertEquals(listOf("s1", "s2"), ids)
    }

    @Test
    fun `update translation persists edited flag and clears retry`() = runBlocking {
        val dao = db.segmentDao()
        dao.replaceForChapter(
            chapterId = 100L,
            segments = listOf(
                DatabaseFixtures.segment("s1", chapterId = 100L, readingOrder = 0, needsRetry = true),
            ),
        )
        dao.updateTranslation("s1", "привет", editedByUser = true)
        val segment = dao.allForChapter(100L).single().toDomain()
        assertEquals("привет", segment.translatedText)
        assertTrue(segment.isEditedByUser)
        assertFalse(segment.needsRetry)
    }
}
