package com.manhwaread.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class DomainResultTest {
    private val failure: DomainResult<Int> = DomainResult.Failure(AppError.SourceUnavailable)
    private val success: DomainResult<Int> = DomainResult.Success(7)

    @Test
    fun `success exposes value and flags`() {
        assertTrue(success.isSuccess)
        assertFalse(success.isFailure)
        assertEquals(7, success.getOrNull())
        assertNull(success.errorOrNull())
    }

    @Test
    fun `failure exposes error and flags`() {
        assertTrue(failure.isFailure)
        assertFalse(failure.isSuccess)
        assertNull(failure.getOrNull())
        assertEquals(AppError.SourceUnavailable, failure.errorOrNull())
    }

    @Test
    fun `map transforms success and passes failure through`() {
        assertEquals(DomainResult.Success(14), success.map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `flatMap chains successes and short-circuits failures`() {
        assertEquals(DomainResult.Success(8), success.flatMap { DomainResult.Success(it + 1) })
        val chained = success.flatMap { DomainResult.Failure(AppError.OcrFailed) }
        assertEquals(DomainResult.Failure(AppError.OcrFailed), chained)
        assertEquals(failure, failure.flatMap { DomainResult.Success(it) })
    }

    @Test
    fun `getOrElse returns value or default`() {
        assertEquals(7, success.getOrElse { -1 })
        assertEquals(-1, failure.getOrElse { -1 })
    }

    @Test
    fun `fold applies matching branch`() {
        assertEquals("v=7", success.fold({ "v=$it" }, { "e" }))
        assertEquals("e=SourceUnavailable", failure.fold({ "v" }, { "e=$it" }))
    }

    @Test
    fun `mapError transforms only failure`() {
        assertEquals(success, success.mapError { AppError.Unknown(IOException("x")) })
        val remapped = failure.mapError { AppError.SourceLayoutChanged }
        assertEquals(DomainResult.Failure(AppError.SourceLayoutChanged), remapped)
    }

    @Test
    fun `capturing wraps value, io and runtime exceptions`() {
        assertEquals(DomainResult.Success(3), DomainResult.capturing { 1 + 2 })

        val ioCause = IOException("net")
        val ioResult = DomainResult.capturing<Int> { throw ioCause }
        assertEquals(DomainResult.Failure(AppError.Network(ioCause)), ioResult)

        val runtime = IllegalStateException("bug")
        val runtimeResult = DomainResult.capturing<Int> { throw runtime }
        assertEquals(DomainResult.Failure(AppError.Unknown(runtime)), runtimeResult)
    }

    @Test
    fun `companion constructors build expected instances`() {
        assertEquals(DomainResult.Success("a"), DomainResult.success("a"))
        assertEquals(DomainResult.Failure(AppError.StorageFull), DomainResult.failure(AppError.StorageFull))
    }
}
