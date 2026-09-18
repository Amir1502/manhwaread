package com.manhwaread.source.madara

// Настройка сайта на теме Madara (WordPress). Все различия между сайтами —
// в этом конфиге: путь каталога, cookie возрастного гейта, параметры сортировки.
data class MadaraConfig(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val lang: String = "en",
    val isNsfw: Boolean = false,
    val mangaPath: String = "/manga",
    // Возрастной гейт (manga18fx и подобные): значение заголовка Cookie.
    val adultCookie: String? = null,
    val orderByPopular: String = "views",
    val orderByLatest: String = "latest",
    // Некоторые сайты отдают главы только через POST admin-ajax.php.
    val useAjaxChapters: Boolean = false,
)

// manga18fx.com — Madara-тема с возрастным гейтом (DoD ФАЗЫ 13).
// VERIFY-API: имя cookie подтверждения возраста может отличаться на сайтах
// той же темы; значение целиком задаётся конфигом и проверяемо без релиза.
fun manga18fxConfig(): MadaraConfig = MadaraConfig(
    id = MANGA18FX_SOURCE_ID,
    name = "Manga18fx",
    baseUrl = "https://manga18fx.com",
    lang = "en",
    isNsfw = true,
    adultCookie = "age_verified=1",
)

private const val MANGA18FX_SOURCE_ID = 2L
