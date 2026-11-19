package com.wusui.androidautoclick.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureRetryPolicyTest {

    @Test
    fun retryWindowIsShortAndFinite() {
        assertEquals(4, GestureRetryPolicy.MAX_CANCEL_RETRIES)
        assertEquals(50L, GestureRetryPolicy.RETRY_DELAY_MS)
        assertEquals(200L, GestureRetryPolicy.RETRY_WINDOW_MS)
        assertTrue(GestureRetryPolicy.RETRY_WINDOW_MS in 100L..200L)
    }

    @Test
    fun retriesWhileAttemptsRemain() {
        assertTrue(GestureRetryPolicy.shouldRetryAfterCancel(4))
        assertTrue(GestureRetryPolicy.shouldRetryAfterCancel(1))
        assertFalse(GestureRetryPolicy.shouldRetryAfterCancel(0))
        assertFalse(GestureRetryPolicy.shouldRetryAfterCancel(-1))
    }

    @Test
    fun exhaustedRetriesSkipRatherThanFailTheRun() {
        assertEquals(InjectOutcome.SKIPPED, GestureRetryPolicy.outcomeWhenRetriesExhausted())
        assertTrue(GestureRetryPolicy.outcomeWhenRetriesExhausted() != InjectOutcome.SUCCESS)
        assertTrue(GestureRetryPolicy.outcomeWhenRetriesExhausted() != InjectOutcome.FAILED)
    }

    @Test
    fun firstCancelStillRetries() {
        var retriesLeft = GestureRetryPolicy.MAX_CANCEL_RETRIES
        var attempts = 1 // initial dispatch
        while (GestureRetryPolicy.shouldRetryAfterCancel(retriesLeft)) {
            retriesLeft--
            attempts++
        }
        assertEquals(GestureRetryPolicy.MAX_CANCEL_RETRIES + 1, attempts)
        assertEquals(InjectOutcome.SKIPPED, GestureRetryPolicy.outcomeWhenRetriesExhausted())
    }
}
