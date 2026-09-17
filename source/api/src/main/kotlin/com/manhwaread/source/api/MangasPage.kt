package com.manhwaread.source.api

/** Страница выдачи каталога: список тайтлов + признак наличия следующей страницы. */
data class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)
