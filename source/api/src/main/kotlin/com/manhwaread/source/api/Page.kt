package com.manhwaread.source.api

/**
 * Страница главы. [imageUrl] может быть null, пока страница не загружена.
 * Закреплённый контракт (AGENTS.md) — не менять.
 */
data class Page(val index: Int, val imageUrl: String?, val status: PageStatus = PageStatus.QUEUE)
