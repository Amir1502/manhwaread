package com.manhwaread.source.madara

// Настройка сайта на теме Madara/MangaStream (WordPress). Все различия между
// сайтами — в этом конфиге: шаблоны URL каталога и поиска, cookie возрастного
// гейта. Плейсхолдер {page} в шаблонах заменяется номером страницы.
data class MadaraConfig(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val lang: String = "en",
    val isNsfw: Boolean = false,
    // Шаблоны каталога: «популярное» и «последние обновления».
    val popularPathTemplate: String = "/manga/page/{page}/?m_orderby=views",
    val latestPathTemplate: String = "/manga/page/{page}/?m_orderby=latest",
    // Поиск: шаблон пути с {page}, имя query-параметра запроса и постоянные
    // дополнительные параметры (классическая Madara: s + post_type=wp-manga).
    val searchPathTemplate: String = "/page/{page}/",
    val searchQueryParam: String = "s",
    val searchExtraParams: Map<String, String> = mapOf(
        "post_type" to "wp-manga",
        "m_orderby" to "relevance",
    ),
    // Возрастной гейт (manga18fx и подобные): значение заголовка Cookie.
    val adultCookie: String? = null,
    // Некоторые сайты отдают главы только через POST admin-ajax.php.
    val useAjaxChapters: Boolean = false,
)

// manga18fx.com — с 2025 сайт переехал с темы Madara на MangaStream:
// популярное /hot-manga?page={n}, последние обновления /page/{n},
// поиск /search?q={query}&page={n}; карточки div.bsx-item/div.hot-item,
// главы li.a-h. Возрастной гейт больше не блокирует страницы, но
// безвредный cookie-заголовок оставлен для совместимости.
fun manga18fxConfig(): MadaraConfig = MadaraConfig(
    id = MANGA18FX_SOURCE_ID,
    name = "Manga18fx",
    baseUrl = "https://manga18fx.com",
    lang = "en",
    isNsfw = true,
    popularPathTemplate = "/hot-manga?page={page}",
    latestPathTemplate = "/page/{page}",
    searchPathTemplate = "/search?page={page}",
    searchQueryParam = "q",
    searchExtraParams = emptyMap(),
    adultCookie = "age_verified=1",
)

private const val MANGA18FX_SOURCE_ID = 2L
