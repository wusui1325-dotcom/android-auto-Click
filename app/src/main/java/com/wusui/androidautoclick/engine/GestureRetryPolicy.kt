package com.wusui.androidautoclick.engine

/**
 * Outcome of one injected stroke (Accessibility dispatchGesture or Root).
 *
 * [SKIPPED] means the stroke could not land (typically an in-flight
 * dispatchGesture cancelled by the user's finger) after retries; the run
 * must continue. It is not a success and must not be counted as one.
 */
enum class InjectOutcome {
    SUCCESS,
    SKIPPED,
    FAILED
}

/**
 * Accessibility dispatchGesture is cancelled by a real finger (true
 * two-pointer inject is not possible on this path). Retry briefly while
 * the finger may still be down; if the stroke still cannot land, skip it
 * instead of failing the whole run.
 */
object GestureRetryPolicy {
    /** Retries after the first cancelled attempt. */
    const val MAX_CANCEL_RETRIES = 4

    /** Delay between cancel retries, in milliseconds. */
    const val RETRY_DELAY_MS = 50L

    /** Total retry window after the first cancel (~200ms). */
    const val RETRY_WINDOW_MS = MAX_CANCEL_RETRIES * RETRY_DELAY_MS

    fun shouldRetryAfterCancel(retriesLeft: Int): Boolean = retriesLeft > 0

    /** When the short retry window is exhausted, skip the stroke, do not fail the run. */
    fun outcomeWhenRetriesExhausted(): InjectOutcome = InjectOutcome.SKIPPED
}
