package com.manhwaread.source.asura

// «Манифест» Asura Scans: все селекторы, пути и параметры в одном месте.
// Если сайт меняет вёрстку, источник возвращает SourceLayoutChanged с
// сообщением «update the manifest» и НЕ падает (DoD ФАЗЫ 13). Обновление
// источника = правка этого файла без изменения логики.
//
// Актуальная раскладка (проверено живой сетью): сайт живёт на
// asurascans.com (сборка Astro), старый домен asuracomic.net редиректит.
// - каталог: /browse?page={n}&sort=popular|latest[&name={query}];
// - карточка тайтла: /comics/{slug};
// - глава: /comics/{slug}/chapter/{n}, страницы рендерятся сервером как
//   <img src="https://cdn.asurascans.com/asura-images/chapters/…">.
data class AsuraManifest(
    val baseUrl: String = "https://asurascans.com",
    val comicListPath: String = "/browse",
    val pageQueryParam: String = "page",
    val sortQueryParam: String = "sort",
    val popularSortValue: String = "popular",
    val latestSortValue: String = "latest",
    val searchQueryParam: String = "name",
    val comicLinkPrefix: String = "/comics/",
    val chapterLinkInfix: String = "/chapter/",
    // Маркер src картинок страниц главы на CDN: отсекает обложки (/covers/)
    // и прочую графику оформления.
    val chapterImageSrcMarker: String = "/asura-images/chapters/",
)

val DEFAULT_ASURA_MANIFEST: AsuraManifest = AsuraManifest()
