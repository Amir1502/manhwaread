# Фикстуры MangaMir (`:source:mangamir`)

Сняты 2026-10-04 (curl, мобильный User-Agent). Статус в карточке определять по словарю текста бейджа («Выпускается» `.badge-info` → ONGOING, «Выпущено» `.badge-warning` → COMPLETED), а не по позиции. HTML обрезан до значимых узлов; сами узлы (теги, атрибуты, классы, Livewire-комментарии) сохранены без изменений. Селекторы парсера не должны зависеть от предков/позиции — только от классов и атрибутов ниже.

| Файл | Источник | Что проверяет |
|---|---|---|
| `catalog_popular_page1.html` | `/manga` (= `sort=views&dir=desc`), 3 из 20 карточек + пагинация | карточки, обложка `_sm`→`_md`, `hasNextPage = true` |
| `catalog_search_korol.html` | `/manga?q=король` | поиск, `hasNextPage = true` |
| `catalog_last_page.html` | `/manga?page=53` (последняя) | 1 карточка, ссылки «Вперёд» нет → `hasNextPage = false` |
| `catalog_empty.html` | синтетический: так сайт отвечает на `?page=60` и пустой поиск (HTTP 200, 0 карточек) | пустой результат без исключений |
| `details_toc.html` | `/manga/korol-mecha?toc` | JSON-LD + DOM; 7 строк списка, из них 1 «Прогноз» |
| `details_broken_jsonld.html` | то же, JSON-LD обрезан посередине | DOM-фолбэк без исключений |
| `chapter_reader.html` | `/manga/korol-mecha/tom-7-glava-302` | 3 из 86 страниц в `[x-data="reader"]` + постер вне контейнера (не должен попасть) |

## Ожидаемые значения

### `catalog_popular_page1.html` — 3 шт., hasNextPage = true
- `ot-goblina-k-bogu-goblinov-a4izl1` · «От гоблина к богу гоблинов» · тип «Маньхуа» · статус «Выпускается» · cover `https://img.mangamir.com/posters/1119/DeURy41N39rT2r2hKzyrwg1Wl8ETBOixDcsTSRiN_sm.jpeg`
- `nachalo-posle-konca-zx5b2g` · «Начало после конца» · тип «OEL Манга» · статус «Выпускается» · cover `https://img.mangamir.com/posters/1021/8ctJMA9oC6IrMb7y56FFogwtBg6CG1efiMZL9Zkr_sm.jpeg`
- `paladin-urovnya-sss-prevoshodyashchiy-zdravyy-smysl` · «Паладин уровня SSS, превосходящий здравый смысл» · тип «Маньхуа» · статус «Выпускается» · cover `https://img.mangamir.com/posters/1134/MvrsHR995hiqX20tS2XGXdtvhyqN9xjmGbzF5rGg_sm.jpeg`

### `catalog_search_korol.html` — 3 шт., hasNextPage = true
- `korol-demonov-podnimayushchiy-svoy-uroven-boevymi-iskusstvami` · «Король демонов, поднимающий свой уровень боевыми искусствами» · тип «Манхва» · статус «Выпускается» · cover `https://img.mangamir.com/posters/1353/86GCeqKwSIw2SN88JxEPfgywJBQLlHHqKPDw0YXk_sm.jpeg`
- `sudnyy-den-ya-korol-virusov` · «Судный День: Я, Король вирусов» · тип «Маньхуа» · статус «Выпускается» · cover `https://img.mangamir.com/posters/1380/kgE3yGoUG2kosfUnyzpAIiBfTckhG6TfgUslFUp4_sm.jpeg`
- `vernuvshis-s-vlastyu-korolya` · «Вернувшись с властью короля» · тип «Манхва» · статус «Выпускается» · cover `https://img.mangamir.com/posters/1109/KpdGohcMz3i16WS583Y5W1ZzZRcUJBhtpetaToup_sm.jpeg`

### `catalog_last_page.html` — 1 шт., hasNextPage = false
- `tishe-edesh-dalshe-budesh` · «Тише едешь — дальше будешь» · тип «Манга» · статус «Выпущено» (`.badge-warning`) → COMPLETED · cover `https://img.mangamir.com/posters/929/F9hv8i73EEyTzVnxgQ4GBNOTnnzflQv6Ji93lyfq_sm.jpeg`

### `catalog_empty.html` — 0 шт., hasNextPage = false

### `details_toc.html`
- title «Король меча»; cover `https://img.mangamir.com/posters/1126/M4A6Kp6dEaHUyZ9Xmg9dr5CvGqLbGU6Rgbf9Cppf.jpeg`; статус «Выпускается» (`/manga?status[0]=Ongoing`) → ONGOING; тип «Манхва» (`/manga?type[0]=Manhwa`)
- rating 9.52 / 10 (23 голосов); возраст «16+» (из `keywords`); жанров в JSON-LD: 32, в DOM-фикстуре `a[href^="/genre/"]` вне карусели: 4 (ещё 6 — внутри «Похожая манга», их не брать); автор и альт. название отсутствуют → null
- описание: брать из DOM `[x-data="showMore"] [x-ref="content"] h2 + div` (≈ 832 символа, 2 `<br>` → `\n`); в JSON-LD `description` обрезан до первой фразы (125 символов) — только фолбэк. Начало: «Рю Хан-Бин попадает в иной мир, где из-з…»
- главы (6, без `tom-7-glava-303` «Прогноз»):

| name | position | slug | number | volume | date (DOM `time[datetime]`) |
|---|---|---|---|---|---|
| Том 7 Глава 302 | 300 | `tom-7-glava-302` | 302 | 7 | 2026-10-04T02:00:03+00:00 |
| Том 7 Глава 301 | 299 | `tom-7-glava-301` | 301 | 7 | 2026-10-02T22:35:28+00:00 |
| Том 7 Глава 300.1 | 298 | `tom-7-glava-300-1` | 300.1 | 7 | 2026-10-02T01:50:06+00:00 |
| Том 7 Глава 300 | 297 | `tom-7-glava-300` | 300 | 7 | 2026-10-02T09:39:44+00:00 |
| Том 1 Глава 1 | 2 | `tom-1-glava-1` | 1 | 1 | 2026-05-30T07:44:44+00:00 |
| Том 1 Глава 0 | 1 | `tom-1-glava-0` | 0 | 1 | 2026-05-30T07:44:44+00:00 |

- «Похожая манга» (`[x-data^="bookCarousel"] a[data-card-link-type="poster"]`, 3 шт.): `kak-vyzhit-v-akademii`, `mladshiy-syn-mechnika`, `nachalo-posle-konca-zx5b2g`. Главный постер — первый `img[src*="/posters/"]` вне карусели.

### `chapter_reader.html` — 3 страницы
- index 0 (data-number 1, 1448×2000): `https://img.mangamir.com/pages/1126/6476abadfff292803a967a732282acbbcfbb4558.webp`
- index 1 (data-number 2, 1448×2000): `https://img.mangamir.com/pages/1126/f3d87ca14fa3acb4292d0b976a99e9bf5f411477.webp`
- index 2 (data-number 3, 1448×2000): `https://img.mangamir.com/pages/1126/9ea74e03605c8a393bd7200cfd044d592b442f5d.webp`

### `details_broken_jsonld.html`
- то же, что `details_toc.html`, но данные из DOM: title из `h1`, cover из `img[src*="/posters/"]`, жанры — 4 (без жанров карусели), главы — 6 (строка «Прогноз» без `time[datetime]` исключена), даты из `time[datetime]`; rating = null.
