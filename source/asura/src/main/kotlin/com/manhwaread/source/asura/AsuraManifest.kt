package com.manhwaread.source.asura

// «Манифест» Asura Scans: все селекторы, пути и параметры в одном месте.
// Если сайт меняет вёрстку, источник возвращает SourceLayoutChanged с
// сообщением «update the manifest» и НЕ падает (DoD ФАЗЫ 13). Обновление
// источника = правка этого файла без изменения логики.
data class AsuraManifest(
    val baseUrl: String = "https://asuracomic.net",
    val comicListPath: String = "/comics",
    val pageQueryParam: String = "page",
    val sortQueryParam: String = "sort",
    val popularSortValue: String = "popular",
    val latestSortValue: String = "latest",
    val searchQueryParam: String = "name",
    val comicLinkPrefix: String = "/comic/",
    val chapterLinkInfix: String = "/chapter-",
    val nextDataElementId: String = "__NEXT_DATA__",
    val imageContainerKeys: Set<String> = setOf("images", "chapterImages", "chapter_images"),
)

val DEFAULT_ASURA_MANIFEST: AsuraManifest = AsuraManifest()
