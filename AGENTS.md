# AGENTS.md — Manhwaread (якорь агентных сессий)

Этот файл — обязательный контракт для любой агентной сессии или разработчика,
работающих с репозиторием. Раздел «Закреплённые контракты» не менять без явного
обоснования в плане сессии.

## Продукт

**Manhwaread** — Android-приложение: парсинг глав манхвы/манги (в первую очередь
корейский/японский raw и английские сканлейты) с сайтов вроде asurascans.com и
manga18fx.com, OCR, автоматический перевод через подключаемый пользователем
AI-API (OpenAI-совместимый, Gemini, DeepL и др.) и вписывание перевода обратно
в облачка реплик. Читалка уровня Mangalib. Целевой язык перевода — русский.

## Ключевое архитектурное решение (не отменять)

Перевод **НЕ запекается в растр по умолчанию**. Он хранится как векторная
инструкция `OverlaySpec` (текст, кегль, позиции строк, параметры шрифта) и
рисуется отдельным слоем поверх оригинала. Это даёт резкость на любом зуме,
мгновенное вкл/выкл, ручное редактирование, смену шрифта без повторного
перевода. Запекание в PNG — только по явному действию (экспорт/CBZ).

## Приоритеты при конфликте

Качество вписывания текста в бабл > надёжность парсинга > скорость перевода >
красота UI > число источников. Стабильность ридера важнее любой фичи.

## Правила генерации/редактирования кода

1. Только код и минимум прозы. Максимум 2 строки пояснения на файл.
2. Файлы полные. Запрещены: `// ... остальной код аналогично`,
   `/* реализация опущена */`, `TODO`, `TODO()`, `NotImplementedError`,
   пустые тела функций, `throw UnsupportedOperationException`.
   Любая объявленная функция — работает.
3. Комментарии в коде — на русском. Идентификаторы, git-коммиты, ключи
   ресурсов — на английском. Значения в `values-ru/strings.xml` — на русском.
4. Не выдумывать API. Только стабильный публичный API библиотек.
   Не уверен в сигнатуре — пометь строку `// VERIFY-API:` с пояснением.
5. Тесты — обязательная часть каждой фазы, не опция. Фаза без тестов
   считается невыполненной.
6. Не добавлять фичи, которых не просили. Не менять закреплённые контракты.
7. Порядок фаз строгий: 0 → 15. Фазу N начинать только после полного
   завершения фазы N-1, включая её тесты.
8. Если фаза велика — выдавать её частями, содержимое не урезая.
9. Коммиты — атомарные, по фазам; сообщение на английском
   (формат `phase-N: <описание>`).

## Вне области (не реализовывать, но заложена расширяемость)

iOS, бэкенд, авторизация, соцсеть, комментарии, платежи, загрузка контента
на внешние серверы, распространение переведённых глав.

## Карта модулей (обязательна)

| Модуль | Тип | Назначение |
|---|---|---|
| `:core:model` | JVM | Доменные модели (Manga, Chapter, Category, History) |
| `:core:common` | JVM | `AppError`, `DomainResult`, retry, circuit breaker, нормализация текста |
| `:core:network` | JVM | OkHttp-фабрика, интерсепторы, rate limiter, Cloudflare-перехват |
| `:core:translation-api` | JVM | Контракты провайдеров перевода, валидация ответов LLM, промт, батчинг |
| `:core:vision-model` | JVM | `Bubble`, `TextSegment`, маски, `TextFitter`, `OverlaySpec` |
| `:core:pipeline` | JVM | Конечный автомат стадий, `ChapterJob`, интерфейсы хранилищ |
| `:source:api` | JVM | Интерфейс `Source`, `SManga`/`SChapter`/`Page`/`Filter` |
| `:source:mangadex` | JVM | Реализация MangaDex API v5 |
| `:source:madara` | JVM | Универсальный адаптер WordPress+Madara |
| `:source:asura` | JVM | Asura Scans |
| `:core:database` | Android | Room |
| `:core:datastore` | Android | DataStore-настройки |
| `:core:designsystem` | Android | Compose-тема |
| `:feature:library` | Android | Экран библиотеки |
| `:feature:browse` | Android | Обзор источников, поиск |
| `:feature:details` | Android | Карточка тайтла |
| `:feature:reader` | Android | Читалка |
| `:feature:history` | Android | История чтения |
| `:feature:downloads` | Android | Загрузки и очередь перевода |
| `:feature:settings` | Android | Настройки |
| `:feature:onboarding` | Android | Онбординг |
| `:app` | Android | Сборка, DI, навигация |

JVM-модули обязаны собираться и тестироваться **без Android SDK**
(`org.jetbrains.kotlin.jvm`, `test { useJUnitPlatform() }`).

## Закреплённые контракты (не менять без явного обоснования)

```kotlin
// :source:api
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
data class SManga(
    val url: String, val title: String, val sourceId: Long,
    val artist: String? = null, val author: String? = null,
    val description: String? = null, val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val thumbnailUrl: String? = null, val nsfw: Boolean = false,
    val initialized: Boolean = false,
)
enum class MangaStatus { UNKNOWN, ONGOING, COMPLETED, HIATUS, CANCELLED }
data class SChapter(
    val url: String, val name: String, val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f, val scanlator: String? = null,
    val read: Boolean = false,
)
data class Page(val index: Int, val imageUrl: String?, val status: PageStatus = PageStatus.QUEUE)
enum class PageStatus { QUEUE, LOAD_PAGE, LOAD_IMAGE, READY, ERROR }
data class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)
```

```kotlin
// :core:common
sealed interface AppError {
    data class Network(val cause: Throwable) : AppError
    data object SourceUnavailable : AppError
    data object SourceLayoutChanged : AppError
    data object CloudflareBlocked : AppError
    data class RateLimited(val retryAfterMs: Long?) : AppError
    data object ProviderAuth : AppError
    data object ProviderQuota : AppError
    data class ProviderBadResponse(val reason: String) : AppError
    data object OcrFailed : AppError
    data object VisionFailed : AppError
    data class TypesetOverflow(val bubbleId: String) : AppError
    data object StorageFull : AppError
    data class Unknown(val cause: Throwable) : AppError
}
```

```kotlin
// :core:vision-model — геометрия СВОЯ (value-классы), НЕ android.graphics.*
data class PointF(val x: Float, val y: Float)
data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)
enum class BubbleKind { SPEECH, THOUGHT, NARRATION_BOX, SHOUT, WHISPER, SFX, OTHER }
enum class DetectedLang { KO, JA, ZH, EN, UNKNOWN }
data class Bubble(
    val id: String, val pageIndex: Int, val polygon: List<PointF>,
    val bounds: RectF, val kind: BubbleKind,
    val tailPoints: List<PointF> = emptyList(),
    val fillColor: Int? = null, val zOrder: Int = 0,
)
data class TextSegment(
    val id: String, val bubbleId: String, val pageIndex: Int,
    val ocrText: String, val ocrLang: DetectedLang,
    val ocrConfidence: Float, val readingOrder: Int,
    val isSfx: Boolean = false, val translatedText: String? = null,
    val isEditedByUser: Boolean = false, val needsRetry: Boolean = false,
)
```

```kotlin
// :core:pipeline
enum class StageStatus { QUEUED, DOWNLOADING, ANALYZING, TRANSLATING, COMPOSITING, DONE, FAILED, CANCELLED }
```

### Контракты ФАЗЫ 5 (TextFitter) — сигнатуры закреплены

```kotlin
// :core:vision-model
interface TextMeasurer {
    fun measureLine(text: String, sizePx: Float, letterSpacing: Float, scaleX: Float): Float
    fun lineHeight(sizePx: Float, lineSpacingMult: Float): Float
}
data class FitConfig(val minSizePx: Float = 14f, val maxSizePx: Float = 128f,
    val stepPx: Float = 1f, val paddingPx: Float = 6f,
    val lineSpacingStart: Float = 1.0f, val lineSpacingMin: Float = 0.9f,
    val letterSpacingMin: Float = -0.02f, val scaleXMin: Float = 0.75f,
    val maxCharsPerLine: Int = 40)
data class FitResult(val lines: List<String>, val sizePx: Float,
    val lineSpacingMult: Float, val letterSpacing: Float, val scaleX: Float,
    val overflow: Boolean, val degradations: Set<Degradation>)
enum class Degradation { LINE_SPACING, SYLLABLE_WRAP, LETTER_SPACING, SCALE_X, CLIPPED }
fun fit(text: String, mask: Mask, measurer: TextMeasurer, config: FitConfig = FitConfig()): FitResult
```

### Контракты ФАЗЫ 6 (LlmResponseValidator) — сигнатуры закреплены

```kotlin
// :core:translation-api
fun validate(rawJson: String, expectedIds: List<String>): ValidationResult
sealed interface ValidationResult {
    data class Valid(val segments: List<TranslatedSegment>) : ValidationResult
    data class Partial(val segments: List<TranslatedSegment>, val missingIds: List<String>,
                       val unexpectedIds: List<String>) : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}
```

## Статус фаз

| Фаза | Описание | Статус |
|---|---|---|
| 0 | План и якорь контрактов (AGENTS.md, README) | ✅ выполнено |
| 1 | Gradle-каркас, все модули | ✅ выполнено |
| 2 | `:core:model` + `:source:api` + тесты | ✅ выполнено |
| 3 | `:core:common` + `:core:network` + тесты | ✅ выполнено |
| 4 | `:source:mangadex` + фикстуры + тесты | ✅ выполнено |
| 5 | `:core:vision-model` (Mask, TextFitter, OverlaySpec) + ≥40 тестов | ✅ выполнено |
| 6 | `:core:translation-api` (валидатор LLM, промт, батчинг) + ≥35 тестов | ✅ выполнено |
| 7 | `:core:pipeline` (StageMachine, ChapterJob) + ≥15 тестов | ✅ выполнено |
| 8 | `:core:database` (Room, миграции, DAO-тесты) | ✅ выполнено |
| 9 | `:app` + Hilt + навигация + тема + CI + Jacoco | ✅ выполнено |
| 10 | Читалка: TiledImageView + OverlayLayer | ✅ выполнено |
| 11 | Vision: OpenCV + ONNX + ML Kit OCR + Inpainting | ✅ выполнено |
| 12 | Провайдеры перевода + Keystore + экраны настроек | ✅ выполнено |
| 13 | Madara + Asura + Cloudflare WebView | ✅ выполнено |
| 14 | UI: каталог, библиотека, история, загрузки, онбординг | ✅ выполнено |
| 15 | Очередь, офлайн, self-check, финал | ✅ выполнено |

## Definition of Done (критерии готовности продукта)

- [x] `./gradlew assembleDebug` без ошибок.
- [ ] MangaDex: поиск → карточка → глава открывается в читалке.
- [x] Madara (manga18fx) работает с age-gate.
- [x] Asura работает или корректно сообщает «обновите манифест», не падает.
- [ ] Читалка: вебтун без швов, зум 5x без тормозов, 800×20000 без OOM,
      4 режима работают.
- [x] Self-check прогоняет пайплайн офлайн.
- [ ] С реальным ключом OpenAI-совместимого провайдера глава переводится,
      русский текст вписан в баблы, выход за границы <5% баблов.
- [x] Тап по баблу → оригинал + перевод; ручная правка переживает перезапуск.
- [x] Офлайн-перевод после скачивания; повторное открытие главы мгновенно.
- [x] Ключи не в логах и не в открытом бэкапе.
- [x] Все unit-тесты зелёные; detekt чист.

Пункты выше, оставшиеся неотмеченными, требуют проверки на реальном
устройстве/с реальной сетью и ключами (не воспроизводятся unit-тестами):
живые источники, производительность читалки на больших страницах и качество
перевода реальным провайдером. Код всех этих путей реализован и покрыт
unit-тестами на уровне контрактов.

Обновление (после фазы 15): Asura и manga18fx проверены живой сетью после
починки парсинга. asuracomic.net переехал на asurascans.com (движок Astro:
каталог /browse, тайтлы /comics/{slug}, главы /comics/{slug}/chapter/{n},
страницы — серверные <img> с CDN-маркером /asura-images/chapters/).
manga18fx.com переехал с темы Madara на MangaStream: популярное /hot-manga,
последние /page/{n}, поиск /search?q=, карточки div.bsx-item/div.hot-item,
главы li.a-h, даты вида «04 Sep 26»; возрастной гейт больше не блокирует
страницы (cookie-заголовок оставлен для совместимости). Пункт MangaDex
остаётся неотмеченным: парсинг API v5 проверен живой сетью (каталог, поиск,
карточка, главы, страницы), но «глава открывается в читалке» требует
реального устройства.

## Запрещено

- «Остальное по аналогии», `TODO`, заглушки.
- Менять закреплённые контракты без обоснования в плане.
- Запекать перевод в растр как единственный вариант.
- Сторонняя аналитика/реклама/трекеры.
- Отправка контента куда-либо кроме источника и выбранного провайдера перевода.

## Окружение разработки (эта машина)

- JDK: Temurin 17.0.20 (`JAVA_HOME` задан глобально).
- Gradle: wrapper 8.14.3; локальная дистрибуция `D:\manhwaread-tools\gradle-8.14.3`
  (используется для верификации, если wrapper ещё не скачал дистрибутив).
- Android SDK: `D:\manhwaread-tools\android-sdk` (platform 35, build-tools 35.0.1),
  подключён через `local.properties` (файл не коммитится).
- Все JVM-модули тестируются без SDK: `gradlew :core:model:test` и т.п.
