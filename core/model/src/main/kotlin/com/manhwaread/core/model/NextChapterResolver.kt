package com.manhwaread.core.model

/**
 * Хронологический компаратор порядка чтения:
 * 1. Сезон по возрастанию ([Chapter.season]);
 * 2. Номер главы по возрастанию ([Chapter.chapterNumber], нераспознанный -1f оказывается в конце);
 * 3. При равенстве — стабильный tie-break по первичному ключу [Chapter.id].
 */
object ChapterReadingOrderComparator : Comparator<Chapter> {
    override fun compare(a: Chapter, b: Chapter): Int {
        val bySeason = a.season.compareTo(b.season)
        if (bySeason != 0) return bySeason

        val aNum = if (a.chapterNumber >= 0f) a.chapterNumber else Float.MAX_VALUE
        val bNum = if (b.chapterNumber >= 0f) b.chapterNumber else Float.MAX_VALUE
        val byNum = aNum.compareTo(bNum)
        if (byNum != 0) return byNum

        return a.id.compareTo(b.id)
    }
}

/**
 * Целевая глава для действия «Читать» в карточке тайтла и читалке.
 */
sealed interface NextChapterTarget {
    /** Главы отсутствуют. */
    data object None : NextChapterTarget

    /** Ни одна глава ещё не прочитана — начать чтение с первой хронологической главы. */
    data class Start(val chapter: Chapter) : NextChapterTarget

    /** Продолжить чтение со следующей непрочитанной главы. */
    data class Resume(val chapter: Chapter) : NextChapterTarget

    /** Все главы прочитаны — предложить перечитать с первой главы. */
    data class ReRead(val chapter: Chapter) : NextChapterTarget
}

val NextChapterTarget.chapterOrNull: Chapter?
    get() = when (this) {
        is NextChapterTarget.None -> null
        is NextChapterTarget.Start -> chapter
        is NextChapterTarget.Resume -> chapter
        is NextChapterTarget.ReRead -> chapter
    }

/**
 * Разрешает следующую главу для чтения в хронологическом порядке:
 * - Все главы прочитаны → [NextChapterTarget.ReRead] (первая глава);
 * - Ни одной главы не прочитано → [NextChapterTarget.Start] (первая глава);
 * - Есть прочитанные → [NextChapterTarget.Resume] (следующая непрочитанная после последней прочитанной
 *   или первая непрочитанная в хронологическом порядке).
 */
object NextChapterResolver {
    fun resolve(
        chapters: List<Chapter>,
        lastReadChapterId: Long? = null,
    ): NextChapterTarget {
        if (chapters.isEmpty()) return NextChapterTarget.None

        val ordered = chapters.sortedWith(ChapterReadingOrderComparator)
        if (ordered.all { it.read }) {
            return NextChapterTarget.ReRead(ordered.first())
        }

        val target = resolveUnreadTarget(ordered, lastReadChapterId)
        return target ?: NextChapterTarget.ReRead(ordered.first())
    }

    private fun resolveUnreadTarget(
        ordered: List<Chapter>,
        lastReadChapterId: Long?,
    ): NextChapterTarget? {
        val noneRead = ordered.none { it.read }
        if (noneRead && lastReadChapterId == null) {
            return NextChapterTarget.Start(ordered.first())
        }

        if (lastReadChapterId != null) {
            val lastReadIndex = ordered.indexOfFirst { it.id == lastReadChapterId }
            if (lastReadIndex != -1) {
                val nextAfterLastRead = ordered.drop(lastReadIndex + 1).firstOrNull { !it.read }
                if (nextAfterLastRead != null) {
                    return NextChapterTarget.Resume(nextAfterLastRead)
                }
            }
        }

        val firstUnread = ordered.firstOrNull { !it.read } ?: return null
        return if (noneRead) {
            NextChapterTarget.Start(firstUnread)
        } else {
            NextChapterTarget.Resume(firstUnread)
        }
    }
}
