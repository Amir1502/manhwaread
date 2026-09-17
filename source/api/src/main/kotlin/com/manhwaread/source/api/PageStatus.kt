package com.manhwaread.source.api

/** Стадия загрузки страницы. Закреплённый контракт (AGENTS.md) — не менять. */
enum class PageStatus { QUEUE, LOAD_PAGE, LOAD_IMAGE, READY, ERROR }
