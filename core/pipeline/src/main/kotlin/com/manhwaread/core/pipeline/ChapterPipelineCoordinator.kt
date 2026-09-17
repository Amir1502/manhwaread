package com.manhwaread.core.pipeline

import com.manhwaread.core.common.AppError
import com.manhwaread.core.common.DomainResult
import com.manhwaread.core.common.flatMap
import com.manhwaread.core.vision.OverlaySpec
import com.manhwaread.core.vision.TextSegment

/**
 * Координатор конвейера главы: проводит задачу по стадиям согласно графу [StageMachine],
 * персистентно фиксируя статусы в [ChapterJobStore]. Ошибка стадии переводит задачу
 * в FAILED с записью ошибки и числа попыток. Повторный запуск задачи, не дошедшей до
 * DONE (включая FAILED/CANCELLED и прерванные стадии), начинает цепочку заново.
 */
class ChapterPipelineCoordinator(
    private val jobStore: ChapterJobStore,
    private val stages: PipelineStages,
) {
    suspend fun run(jobId: String): DomainResult<Unit> {
        val job = jobStore.findById(jobId)
            ?: return DomainResult.failure(AppError.Unknown(IllegalStateException("job not found: $jobId")))
        if (job.state.status == StageStatus.DONE) return DomainResult.success(Unit)
        val machine = StageMachine()
        return stage(job, machine, StageStatus.DOWNLOADING) { stages.downloader.download(job) }
            .flatMap { pages ->
                stage(job, machine, StageStatus.ANALYZING) { stages.analyzer.analyze(job, pages) }
                    .flatMap { segments -> translateAndComposite(job, machine, segments) }
            }
    }

    private suspend fun translateAndComposite(
        job: ChapterJob,
        machine: StageMachine,
        segments: List<TextSegment>,
    ): DomainResult<Unit> =
        stage(job, machine, StageStatus.TRANSLATING) { stages.translator.translate(segments) }
            .flatMap { translated ->
                stage(job, machine, StageStatus.COMPOSITING) { stages.compositor.composite(job, segments, translated) }
            }
            .flatMap { specs -> finish(job, machine, specs) }

    // Переход к стадии + фиксация статуса; ошибка стадии → FAILED с инкрементом попыток.
    private suspend fun <T> stage(
        job: ChapterJob,
        machine: StageMachine,
        target: StageStatus,
        block: suspend () -> DomainResult<T>,
    ): DomainResult<T> {
        if (!machine.transition(target)) {
            return DomainResult.failure(AppError.Unknown(IllegalStateException("illegal stage $target from ${machine.current}")))
        }
        jobStore.update(job.id, job.state.copy(status = machine.current))
        val result = block()
        val error = result.errorOrNull()
        if (error != null) {
            machine.fail()
            jobStore.update(
                job.id,
                job.state.copy(status = machine.current, attempts = job.state.attempts + 1, lastError = error),
            )
        }
        return result
    }

    private suspend fun finish(job: ChapterJob, machine: StageMachine, specs: List<OverlaySpec>): DomainResult<Unit> {
        if (!machine.transition(StageStatus.DONE)) {
            return DomainResult.failure(AppError.Unknown(IllegalStateException("cannot finish from ${machine.current}")))
        }
        stages.overlays.saveChapter(job.ref.chapterId, specs)
        jobStore.update(job.id, job.state.copy(status = StageStatus.DONE, lastError = null))
        return DomainResult.success(Unit)
    }
}
