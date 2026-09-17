package com.manhwaread.core.pipeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StageMachineTest {
    @Test
    fun `starts in queued by default and honors custom initial`() {
        assertEquals(StageStatus.QUEUED, StageMachine().current)
        assertEquals(StageStatus.ANALYZING, StageMachine(StageStatus.ANALYZING).current)
    }

    @Test
    fun `happy path walks all stages to done`() {
        val machine = StageMachine()
        assertTrue(machine.transition(StageStatus.DOWNLOADING))
        assertTrue(machine.transition(StageStatus.ANALYZING))
        assertTrue(machine.transition(StageStatus.TRANSLATING))
        assertTrue(machine.transition(StageStatus.COMPOSITING))
        assertTrue(machine.transition(StageStatus.DONE))
        assertEquals(StageStatus.DONE, machine.current)
        assertTrue(StageStatus.DONE.isTerminal)
    }

    @Test
    fun `illegal transitions rejected without state change`() {
        val machine = StageMachine()
        assertFalse(machine.transition(StageStatus.ANALYZING))
        assertEquals(StageStatus.QUEUED, machine.current)
        assertFalse(machine.canTransition(StageStatus.DONE))
        assertFalse(StageStatus.QUEUED.isTerminal)
    }

    @Test
    fun `done is terminal for every status`() {
        val machine = StageMachine(StageStatus.COMPOSITING)
        assertTrue(machine.transition(StageStatus.DONE))
        StageStatus.entries.forEach { assertFalse(machine.canTransition(it)) }
    }

    @Test
    fun `fail and cancel from working stages`() {
        val failing = StageMachine(StageStatus.DOWNLOADING)
        assertTrue(failing.fail())
        assertEquals(StageStatus.FAILED, failing.current)
        val cancelling = StageMachine(StageStatus.TRANSLATING)
        assertTrue(cancelling.cancel())
        assertEquals(StageStatus.CANCELLED, cancelling.current)
    }

    @Test
    fun `retry requeues failed and cancelled`() {
        val failed = StageMachine(StageStatus.FAILED)
        assertTrue(failed.retry())
        assertEquals(StageStatus.QUEUED, failed.current)
        val cancelled = StageMachine(StageStatus.CANCELLED)
        assertTrue(cancelled.retry())
        assertEquals(StageStatus.QUEUED, cancelled.current)
    }

    @Test
    fun `cannot fail or retry from done`() {
        val machine = StageMachine(StageStatus.DONE)
        assertFalse(machine.fail())
        assertFalse(machine.retry())
    }

    @Test
    fun `history records every transition`() {
        val machine = StageMachine()
        machine.transition(StageStatus.DOWNLOADING)
        machine.fail()
        machine.retry()
        assertEquals(
            listOf(StageStatus.QUEUED, StageStatus.DOWNLOADING, StageStatus.FAILED, StageStatus.QUEUED),
            machine.history,
        )
    }
}
