package com.manhwaread.feature.downloads.selfcheck

import com.manhwaread.core.common.AppError
import com.manhwaread.core.pipeline.ChapterAnalyzer
import com.manhwaread.core.pipeline.PageStore
import com.manhwaread.core.pipeline.StageStatus

// Результат офлайн-самопроверки конвейера: статус, счётчики артефактов, ошибка.
data class SelfCheckResult(
    val success: Boolean,
    val status: StageStatus,
    val segments: Int,
    val overlays: Int,
    val error: AppError? = null,
)

// Контракт запуска самопроверки (DoD «Self-check прогоняет пайплайн офлайн»);
// экран вызывает его без знания внутреннего устройства прогона.
fun interface SelfCheckExecutor {
    suspend fun run(): SelfCheckResult
}

// Фабрика анализатора самопроверки: собирается на собственном PageStore,
// чтобы синтетические страницы не смешивались с кэшем реальных глав.
fun interface SelfCheckAnalyzerFactory {
    fun create(pageStore: PageStore): ChapterAnalyzer
}
