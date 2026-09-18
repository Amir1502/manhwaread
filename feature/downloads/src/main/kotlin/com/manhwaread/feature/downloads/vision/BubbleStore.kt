package com.manhwaread.feature.downloads.vision

import com.manhwaread.core.vision.Bubble
import java.util.concurrent.ConcurrentHashMap

// Геометрия баблов главы: анализатор сохраняет её после детекции, типографика
// и писатель архива главы читают (ФАЗА 15). Живёт в памяти процесса — при
// смерти процесса задача конвейера перезапускается с самого начала.
interface BubbleStore {
    suspend fun saveBubbles(chapterId: Long, bubbles: List<Bubble>)
    suspend fun loadBubbles(chapterId: Long): List<Bubble>
    suspend fun clear(chapterId: Long)
}

class InMemoryBubbleStore : BubbleStore {
    private val chapters = ConcurrentHashMap<Long, List<Bubble>>()

    override suspend fun saveBubbles(chapterId: Long, bubbles: List<Bubble>) {
        chapters[chapterId] = bubbles
    }

    override suspend fun loadBubbles(chapterId: Long): List<Bubble> = chapters[chapterId].orEmpty()

    override suspend fun clear(chapterId: Long) {
        chapters.remove(chapterId)
    }
}
