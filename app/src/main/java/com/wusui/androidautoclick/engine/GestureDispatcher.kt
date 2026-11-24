package com.wusui.androidautoclick.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

/**
 * Strictly serial gesture queue per AccessibilityService.dispatchGesture contract:
 * any in-flight gesture is cancelled by the next one or by a real finger, so we
 * never overlap strokes. Coordinates are screen pixels; times are milliseconds
 * (GestureDescription docs).
 *
 * dispatchGesture cannot inject a second pointer alongside the user's finger.
 * A user touch cancels the in-flight gesture. We retry a few times over
 * [GestureRetryPolicy.RETRY_WINDOW_MS]; if it still cannot land, the stroke is
 * [InjectOutcome.SKIPPED] and playback continues — it is not a fatal failure
 * and not a fake success.
 *
 * Tap stroke duration floor is 1ms (API allows >0). We deliberately do NOT use
 * ViewConfiguration.getTapTimeout() (~100ms) — that is for View tap-vs-longpress,
 * not a GestureDescription minimum; using it made "1ms interval" feel useless.
 */
class GestureDispatcher {
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Queued>()
    private var busy = false
    private var generation = 0

    private data class Queued(
        val gesture: GestureDescription,
        val onDone: (InjectOutcome) -> Unit,
        val retriesLeft: Int = GestureRetryPolicy.MAX_CANCEL_RETRIES
    )

    fun reset() {
        generation++
        queue.clear()
        busy = false
        main.removeCallbacksAndMessages(null)
    }

    fun click(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long,
        onDone: (InjectOutcome) -> Unit
    ) {
        if (!x.isFinite() || !y.isFinite()) {
            onDone(InjectOutcome.FAILED)
            return
        }
        val safeX = x.coerceAtLeast(0f)
        val safeY = y.coerceAtLeast(0f)
        val max = GestureDescription.getMaxGestureDuration()
        // API: stroke duration must be > 0 and ≤ max. No TapTimeout requirement.
        val dur = durationMs.coerceIn(1L, max)
        val gesture = try {
            val path = Path().apply { moveTo(safeX, safeY) }
            val stroke = GestureDescription.StrokeDescription(path, 0, dur)
            GestureDescription.Builder().addStroke(stroke).build()
        } catch (_: Exception) {
            onDone(InjectOutcome.FAILED)
            return
        }
        enqueue(service, gesture, onDone)
    }

    fun swipe(
        service: AccessibilityService,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
        onDone: (InjectOutcome) -> Unit
    ) {
        if (!x1.isFinite() || !y1.isFinite() || !x2.isFinite() || !y2.isFinite()) {
            onDone(InjectOutcome.FAILED)
            return
        }
        val sx1 = x1.coerceAtLeast(0f)
        val sy1 = y1.coerceAtLeast(0f)
        val sx2 = x2.coerceAtLeast(0f)
        val sy2 = y2.coerceAtLeast(0f)
        val max = GestureDescription.getMaxGestureDuration()
        val dur = durationMs.coerceIn(1L, max)
        val gesture = try {
            val path = Path().apply {
                moveTo(sx1, sy1)
                lineTo(sx2, sy2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, dur)
            GestureDescription.Builder().addStroke(stroke).build()
        } catch (_: Exception) {
            onDone(InjectOutcome.FAILED)
            return
        }
        enqueue(service, gesture, onDone)
    }

    private fun enqueue(
        service: AccessibilityService,
        gesture: GestureDescription,
        onDone: (InjectOutcome) -> Unit
    ) {
        // Keep enqueue on main for queue thread-safety
        if (Looper.myLooper() == Looper.getMainLooper()) {
            queue.addLast(Queued(gesture, onDone))
            pump(service)
        } else {
            main.post {
                queue.addLast(Queued(gesture, onDone))
                pump(service)
            }
        }
    }

    private fun pump(service: AccessibilityService) {
        if (busy) return
        val next = queue.pollFirst() ?: return
        busy = true
        val gen = generation
        // Callbacks already arrive on [main] (passed to dispatchGesture) — do not
        // main.post again (that added ~1 frame of dead time per click).
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (gen != generation) return
                busy = false
                next.onDone(InjectOutcome.SUCCESS)
                pump(service)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (gen != generation) return
                busy = false
                if (GestureRetryPolicy.shouldRetryAfterCancel(next.retriesLeft)) {
                    queue.addFirst(next.copy(retriesLeft = next.retriesLeft - 1))
                    main.postDelayed({
                        if (gen != generation) return@postDelayed
                        pump(service)
                    }, GestureRetryPolicy.RETRY_DELAY_MS)
                } else {
                    next.onDone(GestureRetryPolicy.outcomeWhenRetriesExhausted())
                    pump(service)
                }
            }
        }
        val ok = try {
            service.dispatchGesture(next.gesture, cb, main)
        } catch (_: Exception) {
            false
        }
        if (!ok) {
            busy = false
            next.onDone(InjectOutcome.FAILED)
            pump(service)
        }
    }
}
