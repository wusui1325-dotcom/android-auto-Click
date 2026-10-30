package com.wusui.androidautoclick.engine

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.service.ClickAccessibilityService
import com.wusui.androidautoclick.service.ClickNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * Single source of truth for click / swipe / sequence playback.
 * Gestures are strictly serial via [GestureDispatcher].
 */
object ClickEngine {

    private val main = Handler(Looper.getMainLooper())
    private val dispatcher = GestureDispatcher()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var generation = 0
    private var remaining = 0
    private var pointIndex = 0
    private var lastStampMs = 0L
    private var replayIndex = 0
    private var currentLoop = 0
    private var consecutiveFails = 0

    /**
     * @param fromOverlay when true, enforce a short arm delay so the finger-up
     * that tapped the floating button cannot cancel the first dispatchGesture.
     */
    fun start(fromOverlay: Boolean = false): Boolean {
        if (_status.value.running || _status.value.replaying) return true
        if (_status.value.recording) {
            setError("請先停止錄製再開始執行")
            return false
        }
        val cfg = SettingsRepository.config.value
        val points = SettingsRepository.points.value
        val steps = SettingsRepository.sequenceSteps.value
        val service = ClickAccessibilityService.instance
        if (service == null) {
            setError("請先開啟無障礙服務（權限頁）")
            return false
        }
        when (cfg.mode) {
            ClickMode.SWIPE -> if (points.size < 2) {
                setError("滑動模式至少需要 2 個點")
                return false
            }
            ClickMode.SEQUENCE -> if (steps.isEmpty()) {
                setError("序列為空，請先錄製或用標記手動建步")
                return false
            }
            else -> if (points.isEmpty()) {
                setError("沒有點擊座標")
                return false
            }
        }
        generation++
        val gen = generation
        remaining = cfg.count
        pointIndex = 0
        replayIndex = 0
        currentLoop = 0
        consecutiveFails = 0
        dispatcher.reset()
        _status.value = EngineStatus(
            running = cfg.mode != ClickMode.SEQUENCE,
            replaying = cfg.mode == ClickMode.SEQUENCE,
            remaining = remainingLabel(),
            lastError = ""
        )
        haptic(true)
        SettingsRepository.appContext?.let {
            ClickNotification.showRunning(it, "執行中")
        }
        val launch = label@{
            if (gen != generation) return@label
            if (cfg.mode == ClickMode.SEQUENCE) playSequenceStep(gen)
            else tick(gen)
        }
        // Overlay tap finger-up often cancels gesture #1 — arm at least 160ms.
        val arm = maxOf(cfg.startDelayMs, if (fromOverlay) 160L else 0L)
        if (arm > 0) main.postDelayed(launch, arm) else launch()
        return true
    }

    fun stop() {
        val wasActive = _status.value.active
        generation++
        dispatcher.reset()
        main.removeCallbacksAndMessages(null)
        consecutiveFails = 0
        _status.update {
            it.copy(
                running = false,
                replaying = false,
                remaining = remainingLabel()
            )
        }
        if (wasActive) haptic(false)
        SettingsRepository.appContext?.let { ClickNotification.cancel(it) }
    }

    fun toggle(fromOverlay: Boolean = false): Boolean =
        if (_status.value.running || _status.value.replaying) {
            stop(); false
        } else start(fromOverlay = fromOverlay)

    fun clearError() {
        _status.update { it.copy(lastError = "") }
    }

    // ── Recording ──────────────────────────────────────────────

    fun startRecording(clear: Boolean = true): Boolean {
        if (_status.value.running || _status.value.replaying) {
            setError("請先停止執行再錄製")
            return false
        }
        if (ClickAccessibilityService.instance == null) {
            setError("請先開啟無障礙服務才能錄製")
            return false
        }
        SettingsRepository.beginSequenceBatch()
        if (clear) SettingsRepository.clearSequenceSteps()
        lastStampMs = SystemClock.elapsedRealtime()
        _status.update { it.copy(recording = true, lastError = "") }
        return true
    }

    fun stopRecording() {
        if (!_status.value.recording) return
        _status.update { it.copy(recording = false) }
        SettingsRepository.endSequenceBatch()
    }

    fun recordClick(x: Float, y: Float) {
        if (!_status.value.recording) return
        val delay = resolveRecordDelay()
        SettingsRepository.appendSequenceStep(
            SequenceStep(SequenceStep.StepType.CLICK, x = x, y = y, delayMs = delay)
        )
    }

    fun recordSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (!_status.value.recording) return
        val delay = resolveRecordDelay()
        SettingsRepository.appendSequenceStep(
            SequenceStep(
                SequenceStep.StepType.SWIPE,
                x = x1, y = y1, x2 = x2, y2 = y2, delayMs = delay
            )
        )
    }

    fun recordWait(ms: Long) {
        if (!_status.value.recording) return
        SettingsRepository.appendSequenceStep(
            SequenceStep(SequenceStep.StepType.WAIT, delayMs = ms.coerceAtLeast(0))
        )
        lastStampMs = SystemClock.elapsedRealtime()
    }

    /** Build sequence steps from current marker points (manual UX for games). */
    fun importPointsAsSequence(gapMs: Long = 300L) {
        val pts = SettingsRepository.points.value
        if (pts.isEmpty()) {
            setError("沒有標記點可匯入")
            return
        }
        val steps = pts.mapIndexed { i, p ->
            SequenceStep(
                SequenceStep.StepType.CLICK,
                x = p.x, y = p.y,
                delayMs = if (i == 0) 0L else gapMs.coerceAtLeast(0)
            )
        }
        SettingsRepository.setSequenceSteps(steps)
        _status.update { it.copy(lastError = "") }
    }

    private fun resolveRecordDelay(): Long {
        val now = SystemClock.elapsedRealtime()
        val d = if (SettingsRepository.sequenceSteps.value.isEmpty()) 0L
        else (now - lastStampMs).coerceAtLeast(0).coerceAtMost(60_000)
        lastStampMs = now
        return d
    }

    // ── Tick (single / multi / swipe) ──────────────────────────

    private fun tick(gen: Int) {
        if (gen != generation || !_status.value.running) return
        val cfg = SettingsRepository.config.value
        val points = SettingsRepository.points.value
        val service = ClickAccessibilityService.instance
        if (service == null) {
            setError("無障礙服務已斷開")
            stop()
            return
        }
        if (cfg.count >= 0 && remaining <= 0) {
            stop(); return
        }
        if (points.isEmpty()) {
            stop(); return
        }

        when (cfg.mode) {
            ClickMode.SWIPE -> {
                if (points.size < 2) {
                    setError("滑動模式至少需要 2 個點"); stop(); return
                }
                val i0 = pointIndex.coerceIn(0, points.lastIndex - 1)
                val i1 = (i0 + 1).coerceAtMost(points.lastIndex)
                val (x1, y1) = jitter(points[i0], cfg)
                val (x2, y2) = jitter(points[i1], cfg)
                val dur = cfg.durationMs.coerceAtLeast(80)
                dispatcher.swipe(service, x1, y1, x2, y2, dur) { ok ->
                    if (gen != generation) return@swipe
                    if (!handleGestureResult(ok)) return@swipe
                    onActionDone(gen, cfg, points.size)
                    pointIndex = (pointIndex + 1) % (points.size - 1).coerceAtLeast(1)
                    scheduleNext(gen, cfg)
                }
            }
            ClickMode.MULTI -> {
                val idx = pointIndex % points.size
                val (x, y) = jitter(points[idx], cfg)
                dispatcher.click(service, x, y, cfg.durationMs.coerceAtLeast(10)) { ok ->
                    if (gen != generation) return@click
                    if (!handleGestureResult(ok)) return@click
                    onActionDone(gen, cfg, points.size)
                    pointIndex = (pointIndex + 1) % points.size
                    scheduleNext(gen, cfg)
                }
            }
            else -> { // SINGLE
                val (x, y) = jitter(points.first(), cfg)
                dispatcher.click(service, x, y, cfg.durationMs.coerceAtLeast(10)) { ok ->
                    if (gen != generation) return@click
                    if (!handleGestureResult(ok)) return@click
                    onActionDone(gen, cfg, 1)
                    scheduleNext(gen, cfg)
                }
            }
        }
    }

    private fun scheduleNext(gen: Int, cfg: EngineConfig) {
        if (gen != generation || !_status.value.running) return
        main.postDelayed({ tick(gen) }, computeDelay(cfg))
    }

    private fun onActionDone(gen: Int, cfg: EngineConfig, pointCount: Int) {
        if (gen != generation) return
        if (cfg.count >= 0) remaining = (remaining - 1).coerceAtLeast(0)
        _status.update {
            it.copy(
                totalActions = it.totalActions + 1,
                remaining = remainingLabel(),
                currentIndex = pointIndex.coerceIn(0, (pointCount - 1).coerceAtLeast(0))
            )
        }
        SettingsRepository.appContext?.let {
            ClickNotification.showRunning(it, "剩餘 ${remainingLabel()}")
        }
    }

    // ── Sequence replay ────────────────────────────────────────

    private fun playSequenceStep(gen: Int) {
        if (gen != generation || !_status.value.replaying) return
        val steps = SettingsRepository.sequenceSteps.value
        val cfg = SettingsRepository.config.value
        val service = ClickAccessibilityService.instance
        if (service == null) {
            setError("無障礙服務已斷開"); stop(); return
        }
        if (steps.isEmpty()) {
            stop(); return
        }
        if (replayIndex >= steps.size) {
            currentLoop++
            if (cfg.sequenceLoops > 0 && currentLoop >= cfg.sequenceLoops) {
                stop(); return
            }
            replayIndex = 0
        }
        val step = steps[replayIndex]
        replayIndex++
        val gap = step.delayMs.coerceAtLeast(0)
        val run = Runnable {
            if (gen != generation || !_status.value.replaying) return@Runnable
            when (step.type) {
                SequenceStep.StepType.WAIT -> {
                    _status.update { it.copy(totalActions = it.totalActions + 1, currentIndex = replayIndex - 1) }
                    playSequenceStep(gen)
                }
                SequenceStep.StepType.CLICK -> {
                    dispatcher.click(service, step.x, step.y, cfg.durationMs.coerceAtLeast(10)) { ok ->
                        if (gen != generation) return@click
                        if (!handleGestureResult(ok)) return@click
                        _status.update { it.copy(totalActions = it.totalActions + 1, currentIndex = replayIndex - 1) }
                        playSequenceStep(gen)
                    }
                }
                SequenceStep.StepType.SWIPE -> {
                    dispatcher.swipe(
                        service, step.x, step.y, step.x2, step.y2,
                        cfg.durationMs.coerceAtLeast(80)
                    ) { ok ->
                        if (gen != generation) return@swipe
                        if (!handleGestureResult(ok)) return@swipe
                        _status.update { it.copy(totalActions = it.totalActions + 1, currentIndex = replayIndex - 1) }
                        playSequenceStep(gen)
                    }
                }
            }
        }
        if (gap > 0) main.postDelayed(run, gap) else run.run()
    }

    /** Returns false when the run was stopped due to failure. */
    private fun handleGestureResult(ok: Boolean): Boolean {
        if (ok) {
            consecutiveFails = 0
            return true
        }
        consecutiveFails++
        setError("手勢失敗（觸控打斷或無障礙已斷）。已自動重試仍失敗。")
        stop()
        return false
    }

    // ── Helpers ────────────────────────────────────────────────

    private fun computeDelay(cfg: EngineConfig): Long {
        var d = cfg.intervalMs
        if (cfg.randomInterval) d += Random.nextLong(-30, 31)
        return d.coerceAtLeast(20).coerceAtMost(10_000)
    }

    private fun jitter(p: ClickPoint, cfg: EngineConfig): Pair<Float, Float> {
        if (!cfg.randomOffset) return p.x to p.y
        return (p.x + Random.nextInt(-8, 9)) to (p.y + Random.nextInt(-8, 9))
    }

    private fun remainingLabel(): String {
        val c = SettingsRepository.config.value.count
        return if (c < 0) "∞" else remaining.coerceAtLeast(0).toString()
    }

    private fun haptic(start: Boolean) {
        if (!SettingsRepository.config.value.hapticFeedback) return
        val ctx = SettingsRepository.appContext ?: return
        try {
            val vib = ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val effect = android.os.VibrationEffect.createOneShot(
                    if (start) 28 else 18,
                    if (start) 80 else 50
                )
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (start) 28 else 18)
            }
        } catch (_: Exception) {
        }
    }

    private fun setError(msg: String) {
        _status.update { it.copy(lastError = msg) }
    }
}
