package com.manhwaread.core.pipeline

/**
 * Конечный автомат стадий: граф допустимых переходов + история для диагностики.
 * Счастливый путь: QUEUED→DOWNLOADING→ANALYZING→TRANSLATING→COMPOSITING→DONE;
 * из любой рабочей стадии — FAILED/CANCELLED; FAILED/CANCELLED→QUEUED (повтор).
 */
class StageMachine(initial: StageStatus = StageStatus.QUEUED) {
    private val transitions = mutableListOf(initial)

    var current: StageStatus = initial
        private set

    /** История переходов, начиная со стартового статуса. */
    val history: List<StageStatus> get() = transitions.toList()

    fun canTransition(to: StageStatus): Boolean = TRANSITIONS.getValue(current).contains(to)

    /** Выполняет переход; false — если переход недопустим (состояние не меняется). */
    fun transition(to: StageStatus): Boolean {
        if (!canTransition(to)) return false
        current = to
        transitions += to
        return true
    }

    fun fail(): Boolean = transition(StageStatus.FAILED)

    fun cancel(): Boolean = transition(StageStatus.CANCELLED)

    /** Повторная постановка в очередь: FAILED/CANCELLED → QUEUED. */
    fun retry(): Boolean = transition(StageStatus.QUEUED)

    companion object {
        private val TRANSITIONS: Map<StageStatus, Set<StageStatus>> = mapOf(
            StageStatus.QUEUED to setOf(StageStatus.DOWNLOADING, StageStatus.FAILED, StageStatus.CANCELLED),
            StageStatus.DOWNLOADING to setOf(StageStatus.ANALYZING, StageStatus.FAILED, StageStatus.CANCELLED),
            StageStatus.ANALYZING to setOf(StageStatus.TRANSLATING, StageStatus.FAILED, StageStatus.CANCELLED),
            StageStatus.TRANSLATING to setOf(StageStatus.COMPOSITING, StageStatus.FAILED, StageStatus.CANCELLED),
            StageStatus.COMPOSITING to setOf(StageStatus.DONE, StageStatus.FAILED, StageStatus.CANCELLED),
            StageStatus.DONE to emptySet(),
            StageStatus.FAILED to setOf(StageStatus.QUEUED),
            StageStatus.CANCELLED to setOf(StageStatus.QUEUED),
        )
    }
}
