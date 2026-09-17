package com.manhwaread.core.source

// Внутренний контракт приложения; совместимость ABI с расширениями требует отдельного адаптера.
interface Source {
    val id: Long
    val name: String
    val lang: String
    val baseUrl: String
    val supportsSearch: Boolean
    val isNsfw: Boolean

    suspend fun getPopular(page: Int): MangasPage
    suspend fun getLatest(page: Int): MangasPage
    suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage
    suspend fun getDetails(manga: SManga): SManga
    suspend fun getChapterList(manga: SManga): List<SChapter>
    suspend fun getPageList(chapter: SChapter): List<Page>
    suspend fun getMangaUrl(manga: SManga): String?
    suspend fun getChapterUrl(chapter: SChapter): String?
}

data class SManga(
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val status: Int,
    val thumbnailUrl: String?,
    val sourceId: Long,
    val nsfw: Boolean,
    val initialized: Boolean = false,
)

data class SChapter(
    val url: String,
    val name: String,
    val dateUpload: Long,
    val chapterNumber: Float,
    val scanlator: String?,
    val read: Boolean = false,
)

data class Page(val index: Int, val imageUrl: String?, val status: Int = 0)
data class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)

sealed interface Filter {
    data class Languages(val values: Set<String>) : Filter {
        init { require(values.isNotEmpty() && values.all { it.matches(Regex("[a-z]{2}(-[a-z]{2})?")) }) }
    }
}

object MangaStatus {
    const val UNKNOWN = 0
    const val ONGOING = 1
    const val COMPLETED = 2
    const val HIATUS = 3
    const val CANCELLED = 4
}
