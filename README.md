# Manhwaread

Android-читалка манги и манхвы: парсинг глав из каталогов, OCR облачков,
перевод через подключаемый пользователем AI-API и векторное вписывание
русского перевода обратно в баблы (без запекания в растр — резкость на любом зуме).

**Статус: ФАЗА 8 — `:core:database` (Room: сущности, DAO, конвертеры, DAO-тесты)** (см. таблицу фаз в `AGENTS.md`).

## Сборка

- Требуется JDK 17 и Android SDK 35 (путь — в `local.properties`).
- JVM-модули (вся доменная логика) собираются и тестируются без Android SDK.

```bash
./gradlew projects                 # список модулей
./gradlew :core:model:test         # тесты JVM-модуля
./gradlew :app:assembleDebug       # сборка APK
./gradlew detekt ktlintCheck       # статический анализ
```

## Карта модулей

Полная карта и закреплённые контракты — в [`AGENTS.md`](AGENTS.md).
