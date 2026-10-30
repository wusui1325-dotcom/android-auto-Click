package com.wusui.androidautoclick.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.wusui.androidautoclick.R
import com.wusui.androidautoclick.data.AppStore
import com.wusui.androidautoclick.i18n.LocaleHelper
import com.wusui.androidautoclick.service.ClickAccessibilityService
import com.wusui.androidautoclick.service.ClickNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * Plays ordered TouchNodes in cycles. Gestures strictly serial via [GestureDispatcher].
 */
object ClickEngine {

    private fun msg(id: Int): String {
        val ctx = AppStore.appContext ?: return ""
        return LocaleHelper.str(ctx, id)
    }

    private fun msg(id: Int, vararg args: Any): String {
        val ctx = AppStore.appContext ?: return ""
        return LocaleHelper.str(ctx, id, *args)
    }

    private val main = Handler(Looper.getMainLooper())
    private val dispatcher = GestureDispatcher()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var generation = 0
    private var cycleIndex = 0
    private var playIndex = 0 // index in flattened play list
    private var startElapsed = 0L
    private var lastRecordStamp = 0L
    private var consecutiveFails = 0

    private data class PlayItem(
        val node: TouchNode,
        val end: TouchNode? = null // for drag
    )

    fun start(fromOverlay: Boolean = false): Boolean {
        if (_status.value.running) return true
        if (_status.value.recording) {
            setError(msg(R.string.err_stop_recording))
            return false
        }
        val service = ClickAccessibilityService.instance
        if (service == null) {
            setError(msg(R.string.err_need_a11y))
            return false
        }
        val items = buildPlayList()
        if (items.isEmpty()) {
            setError(msg(R.string.err_no_points))
            return false
        }
        generation++
        val gen = generation
        cycleIndex = 0
        playIndex = 0
        consecutiveFails = 0
        startElapsed = SystemClock.elapsedRealtime()
        dispatcher.reset()
        _status.value = EngineStatus(running = true, message = msg(R.string.status_running), lastError = "")
        AppStore.appContext?.let { ClickNotification.showRunning(it, msg(R.string.status_running)) }
        val arm = if (fromOverlay) 160L else 0L
        if (arm > 0) main.postDelayed({ if (gen == generation) tick(gen) }, arm)
        else tick(gen)
        return true
    }

    fun stop() {
        val was = _status.value.running
        generation++
        dispatcher.reset()
        main.removeCallbacksAndMessages(null)
        consecutiveFails = 0
        _status.update {
            it.copy(running = false, message = if (was) msg(R.string.status_stopped) else it.message)
        }
        AppStore.appContext?.let { ClickNotification.cancel(it) }
    }

    fun toggle(fromOverlay: Boolean = false): Boolean =
        if (_status.value.running) {
            stop(); false
        } else start(fromOverlay)

    fun clearError() {
        _status.update { it.copy(lastError = "") }
    }

    // ── Recording ──────────────────────────────────────────────

    fun startRecording(clear: Boolean = false): Boolean {
        if (_status.value.running) {
            setError(msg(R.string.err_stop_running))
            return false
        }
        if (ClickAccessibilityService.instance == null) {
            setError(msg(R.string.err_need_a11y))
            return false
        }
        if (clear) AppStore.clearNodes()
        lastRecordStamp = SystemClock.elapsedRealtime()
        _status.update { it.copy(recording = true, lastError = "", message = msg(R.string.status_recording)) }
        return true
    }

    fun stopRecording() {
        _status.update { it.copy(recording = false, message = msg(R.string.status_record_done)) }
    }

    fun toggleRecording(): Boolean {
        return if (_status.value.recording) {
            stopRecording(); false
        } else startRecording(clear = AppStore.nodes.value.isEmpty())
    }

    fun onAccessibilityClick(x: Float, y: Float) {
        if (!_status.value.recording) return
        val now = SystemClock.elapsedRealtime()
        val gap = if (AppStore.nodes.value.isEmpty()) 0L
        else (now - lastRecordStamp).coerceAtLeast(0).coerceAtMost(60_000)
        lastRecordStamp = now
        AppStore.appendRecordedClick(x, y, gap)
    }

    // ── Playback ───────────────────────────────────────────────

    private fun buildPlayList(): List<PlayItem> {
        val ordered = AppStore.orderedNodes()
        val out = mutableListOf<PlayItem>()
        val seenGroups = mutableSetOf<String>()
        for (n in ordered) {
            when (n.kind) {
                NodeKind.CLICK -> out += PlayItem(n)
                NodeKind.DRAG_START -> {
                    val gid = n.groupId ?: continue
                    if (!seenGroups.add(gid)) continue
                    val end = ordered.find { it.groupId == gid && it.kind == NodeKind.DRAG_END }
                    out += PlayItem(n, end)
                }
                NodeKind.DRAG_END -> { /* paired with start */ }
            }
        }
        return out
    }

    private fun tick(gen: Int) {
        if (gen != generation || !_status.value.running) return
        val service = ClickAccessibilityService.instance
        if (service == null) {
            setError(msg(R.string.err_a11y_disconnected)); stop(); return
        }
        val params = AppStore.params.value
        if (params.stopAfterMs > 0) {
            val elapsed = SystemClock.elapsedRealtime() - startElapsed
            if (elapsed >= params.stopAfterMs) {
                stop(); return
            }
        }
        val items = buildPlayList()
        if (items.isEmpty()) {
            stop(); return
        }
        if (playIndex >= items.size) {
            cycleIndex++
            if (params.cycleCount > 0 && cycleIndex >= params.cycleCount) {
                stop(); return
            }
            playIndex = 0
        }
        val item = items[playIndex]
        playIndex++
        // After last play item, next-cycle wait = last ordered touch node's intervalToNextMs
        val wrapping = playIndex >= items.size
        val gapAfter = if (wrapping) {
            AppStore.orderedNodes().lastOrNull()?.intervalToNextMs ?: item.node.intervalToNextMs
        } else {
            item.node.intervalToNextMs
        }
        _status.update {
            it.copy(
                currentOrder = item.node.order,
                cycleIndex = cycleIndex,
                message = msg(R.string.status_cycle_order, cycleIndex + 1, item.node.order)
            )
        }

        when {
            item.node.kind == NodeKind.DRAG_START && item.end != null -> {
                val (x1, y1) = jitter(item.node.x, item.node.y, params)
                val (x2, y2) = jitter(item.end.x, item.end.y, params)
                dispatcher.swipe(service, x1, y1, x2, y2, params.swipeDurationMs) { ok ->
                    if (gen != generation) return@swipe
                    if (!okResult(ok)) return@swipe
                    bumpAction()
                    scheduleAfter(gen, gapAfter)
                }
            }
            else -> {
                runClicks(gen, service, item.node, params, 0, gapAfter)
            }
        }
    }

    private fun runClicks(
        gen: Int,
        service: android.accessibilityservice.AccessibilityService,
        node: TouchNode,
        params: GlobalParams,
        done: Int,
        gapAfter: Long
    ) {
        if (gen != generation || !_status.value.running) return
        if (done >= node.clickCount.coerceAtLeast(1)) {
            scheduleAfter(gen, gapAfter)
            return
        }
        val (x, y) = jitter(node.x, node.y, params)
        dispatcher.click(service, x, y, node.tapDurationMs.coerceAtLeast(1L)) { ok ->
            if (gen != generation) return@click
            if (!okResult(ok)) return@click
            bumpAction()
            // No extra delay: GestureDispatcher is already strictly serial.
            // Next tap / interval waits only for onCompleted + gapAfter.
            runClicks(gen, service, node, params, done + 1, gapAfter)
        }
    }

    private fun scheduleAfter(gen: Int, gap: Long) {
        if (gen != generation || !_status.value.running) return
        val wait = gap.coerceAtLeast(0)
        if (wait == 0L) tick(gen)
        else main.postDelayed({ tick(gen) }, wait)
    }

    private fun bumpAction() {
        _status.update { it.copy(totalActions = it.totalActions + 1) }
        val n = _status.value.totalActions
        // Avoid notifying every tap (binder cost) — every 8th + first
        if (n == 1 || n % 8 == 0) {
            AppStore.appContext?.let {
                ClickNotification.showRunning(it, msg(R.string.notif_executed, n))
            }
        }
    }

    private fun okResult(ok: Boolean): Boolean {
        if (ok) {
            consecutiveFails = 0
            return true
        }
        consecutiveFails++
        setError(msg(R.string.err_gesture_failed))
        stop()
        return false
    }

    private fun jitter(x: Float, y: Float, params: GlobalParams): Pair<Float, Float> {
        if (!params.jitter) return x to y
        return (x + Random.nextInt(-8, 9)) to (y + Random.nextInt(-8, 9))
    }

    private fun setError(msg: String) {
        _status.update { it.copy(lastError = msg) }
    }
}
