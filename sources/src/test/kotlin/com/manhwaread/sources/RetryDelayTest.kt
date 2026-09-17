package com.manhwaread.sources

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RetryDelayTest {
    @Test fun parsesSecondsAndRejectsInvalidHeaders() {
        assertEquals(3_000L, RetryDelay.parseMillis("3", 0))
        assertEquals(0L, RetryDelay.parseMillis("-1", 0))
        assertNull(RetryDelay.parseMillis("later", 0))
        assertNull(RetryDelay.parseMillis(null, 0))
    }

    @Test fun parsesHttpDates() {
        val now = Instant.parse("2015-10-21T07:27:00Z").toEpochMilli()
        assertEquals(60_000L, RetryDelay.parseMillis("Wed, 21 Oct 2015 07:28:00 GMT", now))
        assertEquals(0L, RetryDelay.parseMillis("Wed, 21 Oct 2015 07:26:00 GMT", now))
    }

    @Test fun doesNotOverflowSeconds() {
        assertTrue(RetryDelay.parseMillis(Long.MAX_VALUE.toString(), 0)!! > 0)
    }
}
