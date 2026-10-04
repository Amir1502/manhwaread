package com.manhwaread.source.mangamir

import okhttp3.HttpUrl.Companion.toHttpUrl

internal object MangaMirUrls {
    const val DEFAULT_BASE_URL = "https://mangamir.com"
    const val SORT_POPULAR = "views"
    const val SORT_LATEST = "last_chapter_id"
    const val DIR_DESC = "desc"

    private val COVER_SUFFIX_REGEX = Regex("""_(sm|md)(?=\.(jpe?g|png|webp)$)""")

    fun upgradeCoverToMd(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.replace(COVER_SUFFIX_REGEX, "_md")
    }

    fun removeCoverSuffix(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.replace(COVER_SUFFIX_REGEX, "")
    }

    fun buildCatalogUrl(baseUrl: String, page: Int, sort: String = SORT_POPULAR, dir: String = DIR_DESC): String {
        return "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("dir", dir)
            .addQueryParameter("page", page.toString())
            .build()
            .toString()
    }

    fun buildSearchUrl(baseUrl: String, query: String, page: Int): String {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return buildCatalogUrl(baseUrl, page, SORT_POPULAR, DIR_DESC)
        }
        return "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmed)
            .addQueryParameter("page", page.toString())
            .build()
            .toString()
    }

    fun buildDetailsUrl(baseUrl: String, mangaUrl: String): String {
        val normalized = normalizePath(mangaUrl)
        val path = if (normalized.endsWith("?toc")) normalized else "$normalized?toc"
        return "$baseUrl$path"
    }

    fun buildChapterUrl(baseUrl: String, chapterUrl: String): String {
        val normalized = normalizePath(chapterUrl)
        return "$baseUrl$normalized"
    }

    fun slugFromMangaUrl(url: String): String {
        val path = if (url.startsWith("http://") || url.startsWith("https://")) {
            url.toHttpUrl().encodedPath
        } else {
            url.substringBefore('?')
        }
        return path.trim('/').substringAfterLast('/')
    }

    fun normalizePath(url: String): String {
        val path = if (url.startsWith("http://") || url.startsWith("https://")) {
            val parsed = url.toHttpUrl()
            val query = if (parsed.encodedQuery != null) "?${parsed.encodedQuery}" else ""
            "${parsed.encodedPath}$query"
        } else {
            url
        }
        return if (path.startsWith("/")) path else "/$path"
    }
}
