package com.manhwaread.core.pipeline

/** Стадии конвейера обработки главы (ЗАКРЕПЛЁННЫЙ КОНТРАКТ ФАЗЫ 7 — AGENTS.md). */
enum class StageStatus { QUEUED, DOWNLOADING, ANALYZING, TRANSLATING, COMPOSITING, DONE, FAILED, CANCELLED }

/** Терминальные состояния: работа больше не идёт (FAILED/CANCELLED можно вернуть в очередь). */
val StageStatus.isTerminal: Boolean
    get() = this == StageStatus.DONE || this == StageStatus.FAILED || this == StageStatus.CANCELLED
