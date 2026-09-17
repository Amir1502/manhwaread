package com.manhwaread.core.model

/**
 * Закладка на страницу главы (с необязательной заметкой).
 */
data class Bookmark(
    val id: Long = 0L,
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    val createdAtMs: Long = 0L,
    val note: String? = null,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be >= 0, got $pageIndex" }
    }
}
