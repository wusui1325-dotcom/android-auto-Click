package com.wusui.androidautoclick.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

/**
 * Strictly serial gesture queue per AccessibilityService.dispatchGesture contract:
 * any in-flight gesture is cancelled by the next one / user touch, so we never
 * overlap strokes. Coordinates are screen pixels; times are milliseconds
 * (GestureDescription docs).
 *
 * Soft-retry once on cancel (user finger / system interrupt) before reporting failure.
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
        val onDone: (Boolean) -> Unit,
        val retriesLeft: Int = 1
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
        onDone: (Boolean) -> Unit
    ) {
        val max = GestureDescription.getMaxGestureDuration()
        // API: stroke duration must be > 0 and ≤ max. No TapTimeout requirement.
        val dur = durationMs.coerceIn(1L, max)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, dur)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        enqueue(service, gesture, onDone)
    }

    fun swipe(
        service: AccessibilityService,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
        onDone: (Boolean) -> Unit
    ) {
        val max = GestureDescription.getMaxGestureDuration()
        val dur = durationMs.coerceIn(1L, max)
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, dur)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        enqueue(service, gesture, onDone)
    }

    private fun enqueue(
        service: AccessibilityService,
        gesture: GestureDescription,
        onDone: (Boolean) -> Unit
    ) {
        // Keep enqueue on main for queue thread-safety
        if (Looper.myLooper() == Looper.getMainLooper()) {
            queue.addLast(Queued(gesture, onDone, retriesLeft = 1))
            pump(service)
        } else {
            main.post {
                queue.addLast(Queued(gesture, onDone, retriesLeft = 1))
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
                next.onDone(true)
                pump(service)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (gen != generation) return
                busy = false
                if (next.retriesLeft > 0) {
                    queue.addFirst(next.copy(retriesLeft = next.retriesLeft - 1))
                    main.postDelayed({
                        if (gen != generation) return@postDelayed
                        pump(service)
                    }, 40)
                } else {
                    next.onDone(false)
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
            next.onDone(false)
            pump(service)
        }
    }
}
