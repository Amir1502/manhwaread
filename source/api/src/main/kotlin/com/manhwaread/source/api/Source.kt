package com.manhwaread.source.api

/**
 * Контракт источника каталога манги/манхвы.
 * Все методы — suspend; источник сам отвечает за троттлинг и корректные URL.
 * Закреплённый контракт (AGENTS.md) — не менять.
 */
interface Source {
    val id: Long
    val name: String
    val lang: String            // "en" | "ko" | "ja" | "multi"
    val baseUrl: String
    val supportsSearch: Boolean
    val isNsfw: Boolean
    suspend fun getPopular(page: Int): MangasPage
    suspend fun getLatest(page: Int): MangasPage
    suspend fun search(query: String, filters: List<Filter>, page: Int): MangasPage
    suspend fun getDetails(manga: SManga): SManga
    suspend fun getChapterList(manga: SManga): List<SChapter>
    suspend fun getPageList(chapter: SChapter): List<Page>
}
