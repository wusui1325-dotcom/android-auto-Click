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
    private var startTimeMs = 0L
    private var lastStampMs = 0L
    private var replayIndex = 0
    private var currentLoop = 0

    fun start(): Boolean {
        if (_status.value.running || _status.value.replaying) return true
        val cfg = SettingsRepository.config.value
        val points = SettingsRepository.points.value
        val steps = SettingsRepository.sequenceSteps.value
        val service = ClickAccessibilityService.instance
        if (service == null) {
            setError("請先開啟無障礙服務")
            return false
        }
        when (cfg.mode) {
            ClickMode.SWIPE -> if (points.size < 2) {
                setError("滑動模式至少需要 2 個點")
                return false
            }
            ClickMode.SEQUENCE -> if (steps.isEmpty()) {
                setError("序列為空，請先錄製或新增步驟")
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
        startTimeMs = System.currentTimeMillis()
        dispatcher.reset()
        _status.value = EngineStatus(
            running = cfg.mode != ClickMode.SEQUENCE,
            replaying = cfg.mode == ClickMode.SEQUENCE,
            remaining = remainingLabel(),
            lastError = ""
        )
        SettingsRepository.appContext?.let {
            ClickNotification.showRunning(it, "執行中")
        }
        val launch = label@{
            if (gen != generation) return@label
            if (cfg.mode == ClickMode.SEQUENCE) playSequenceStep(gen)
            else tick(gen)
        }
        if (cfg.startDelayMs > 0) main.postDelayed(launch, cfg.startDelayMs) else launch()
        return true
    }

    fun stop() {
        generation++
        dispatcher.reset()
        main.removeCallbacksAndMessages(null)
        val was = _status.value
        _status.update {
            it.copy(
                running = false,
                replaying = false,
                recording = false,
                remaining = remainingLabel()
            )
        }
        if (was.recording) {
            // keep steps; just stop recording flag
        }
        SettingsRepository.appContext?.let { ClickNotification.cancel(it) }
    }

    fun toggle(): Boolean = if (_status.value.running || _status.value.replaying) {
        stop(); false
    } else start()

    fun clearError() {
        _status.update { it.copy(lastError = "") }
    }

    // ── Recording ──────────────────────────────────────────────

    fun startRecording(clear: Boolean = true) {
        if (clear) SettingsRepository.clearSequenceSteps()
        lastStampMs = SystemClock.elapsedRealtime()
        _status.update { it.copy(recording = true, lastError = "") }
    }

    fun stopRecording() {
        _status.update { it.copy(recording = false) }
        SettingsRepository.persistSequences()
    }

    fun recordClick(x: Float, y: Float) {
        if (!_status.value.recording) return
        val delay = resolveRecordDelay()
        SettingsRepository.appendSequenceStep(
            SequenceStep(SequenceStep.StepType.CLICK, x = x, y = y, delayMs = delay)
        )
    }

    fun recordWait(ms: Long) {
        if (!_status.value.recording) return
        SettingsRepository.appendSequenceStep(
            SequenceStep(SequenceStep.StepType.WAIT, delayMs = ms.coerceAtLeast(0))
        )
        lastStampMs = SystemClock.elapsedRealtime()
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
                    if (!ok) {
                        setError("手勢失敗"); stop(); return@swipe
                    }
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
                    if (!ok) {
                        setError("手勢失敗"); stop(); return@click
                    }
                    onActionDone(gen, cfg, points.size)
                    pointIndex = (pointIndex + 1) % points.size
                    scheduleNext(gen, cfg)
                }
            }
            else -> { // SINGLE
                val (x, y) = jitter(points.first(), cfg)
                dispatcher.click(service, x, y, cfg.durationMs.coerceAtLeast(10)) { ok ->
                    if (gen != generation) return@click
                    if (!ok) {
                        setError("手勢失敗"); stop(); return@click
                    }
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
                        if (!ok) {
                            setError("手勢失敗"); stop(); return@click
                        }
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
                        if (!ok) {
                            setError("手勢失敗"); stop(); return@swipe
                        }
                        _status.update { it.copy(totalActions = it.totalActions + 1, currentIndex = replayIndex - 1) }
                        playSequenceStep(gen)
                    }
                }
            }
        }
        if (gap > 0) main.postDelayed(run, gap) else run.run()
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

    private fun setError(msg: String) {
        _status.update { it.copy(lastError = msg) }
    }
}
