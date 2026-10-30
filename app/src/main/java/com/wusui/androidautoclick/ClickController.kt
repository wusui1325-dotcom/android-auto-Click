package com.wusui.androidautoclick

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wusui.androidautoclick.service.ClickAccessibilityService

object ClickController {

    var appContext: Context? = null

    var isRunning by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var startTimeMs by mutableLongStateOf(0L)
        private set
    var totalClicks by mutableIntStateOf(0)
        private set
    var lastError by mutableStateOf("")
        private set
    var remainingDisplay by mutableStateOf("")
        private set
    var currentPointIndex by mutableIntStateOf(0)
        private set

    var intervalMs by mutableLongStateOf(200L)
    var durationMs by mutableLongStateOf(50L)
    var count by mutableIntStateOf(-1)
    var mode by mutableStateOf("單點")
    val points = mutableStateListOf(540f to 960f)
    var startDelayMs by mutableLongStateOf(0L)
    var maxCps by mutableIntStateOf(20)
    var maxMinutes by mutableIntStateOf(0)
    var randomOffset by mutableStateOf(false)
    var randomInterval by mutableStateOf(false)
    var antiDetect by mutableStateOf(false)
    var humanize by mutableStateOf(false)
    var burstCount by mutableIntStateOf(1)
    var burstPause by mutableLongStateOf(0L)
    var warmupClicks by mutableIntStateOf(0)
    var slowStart by mutableStateOf(false)
    var invertInterval by mutableStateOf(false)
    var decelerate by mutableStateOf(false)
    var skipEvery by mutableIntStateOf(0)
    var doubleTap by mutableStateOf(false)
    var longPress by mutableStateOf(false)
    var jitterInterval by mutableStateOf(false)
    var onlyEven by mutableStateOf(false)
    var onlyOdd by mutableStateOf(false)
    var reversePoints by mutableStateOf(false)
    var pingPong by mutableStateOf(false)
    var randomPoint by mutableStateOf(false)
    var clampToScreen by mutableStateOf(true)
    var logClicks by mutableStateOf(true)
    var logEvery by mutableIntStateOf(1)
    var stopAtTotal by mutableIntStateOf(0)
    var pauseEvery by mutableIntStateOf(0)
    var safeMode by mutableStateOf(false)
    var debugLog by mutableStateOf(false)
    var vibrateOnStart by mutableStateOf(false)
    var showToast by mutableStateOf(false)
    var endBeep by mutableStateOf(false)
    var mirrorX by mutableStateOf(false)
    var mirrorY by mutableStateOf(false)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var scale by mutableStateOf(1f)
    var showCoordsOnOverlay by mutableStateOf(false)
    var clearLogOnStart by mutableStateOf(false)
    var keepScreenOn by mutableStateOf(false)
    var soundOnClick by mutableStateOf(false)
    var stopOnError by mutableStateOf(true)
    var showMarkers by mutableStateOf(true)

    val log = mutableStateListOf<String>()
    val favoritePoints = mutableStateListOf<Pair<Float, Float>>()

    private val handler = Handler(Looper.getMainLooper())
    private var remaining: Int = 0
    private var pointIndex = 0
    private var pingDir = 1
    private var lastNotifyMs = 0L
    private var gestureInFlight = false
    private var generation = 0
    private var tone: ToneGenerator? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun elapsedSec(): Long = if (isRunning) (System.currentTimeMillis() - startTimeMs) / 1000 else 0

    fun pause() {
        isPaused = true
        addLog("暫停")
    }

    fun resume() {
        isPaused = false
        addLog("繼續")
    }

    fun clearError() {
        lastError = ""
    }

    fun resetTotal() {
        totalClicks = 0
    }

    fun addPoint(x: Float, y: Float) {
        points.add(x to y)
        SettingsStore.savePoints()
    }

    fun removeLastPoint() {
        if (points.size > 1) {
            points.removeAt(points.lastIndex)
            SettingsStore.savePoints()
        }
    }

    fun clearPoints() {
        points.clear()
        points.add(540f to 960f)
        SettingsStore.savePoints()
    }

    fun duplicateLastPoint() {
        points.lastOrNull()?.let {
            points.add(it)
            SettingsStore.savePoints()
        }
    }

    fun nudgeLastPoint(dx: Float, dy: Float) {
        if (points.isEmpty()) return
        val last = points.last()
        points[points.lastIndex] = (last.first + dx) to (last.second + dy)
        SettingsStore.savePoints()
    }

    fun updatePoint(index: Int, x: Float, y: Float) {
        if (index !in points.indices) return
        points[index] = x to y
        SettingsStore.savePoints()
    }

    fun saveFavorite() {
        points.lastOrNull()?.let {
            favoritePoints.add(it)
            SettingsStore.saveFavorites()
        }
    }

    fun loadFavorite(index: Int) {
        favoritePoints.getOrNull(index)?.let {
            if (points.isEmpty()) points.add(it) else points[points.lastIndex] = it
            SettingsStore.savePoints()
        }
    }

    fun clearFavorites() {
        favoritePoints.clear()
        SettingsStore.saveFavorites()
    }

    fun resetDefaults() {
        intervalMs = 200
        durationMs = 50
        count = -1
        mode = "單點"
        randomOffset = false
        randomInterval = false
        antiDetect = false
        startDelayMs = 0
        clearPoints()
        SettingsStore.save()
    }

    private fun addLog(msg: String) {
        log.add(0, msg)
        while (log.size > 20) log.removeAt(log.lastIndex)
    }

    private fun updateRemaining() {
        remainingDisplay = if (count < 0) "∞" else remaining.coerceAtLeast(0).toString()
    }

    private fun toneGen(): ToneGenerator {
        val existing = tone
        if (existing != null) return existing
        return try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50).also { tone = it }
        } catch (_: Exception) {
            ToneGenerator(AudioManager.STREAM_MUSIC, 50).also { tone = it }
        }
    }

    private fun releaseTone() {
        try {
            tone?.release()
        } catch (_: Exception) {
        }
        tone = null
    }

    fun start(): Boolean {
        if (isRunning) return true
        if (points.isEmpty()) {
            lastError = "沒有點擊座標"
            return false
        }
        if (mode == "滑動" && points.size < 2) {
            lastError = "滑動模式至少需要 2 個點"
            return false
        }
        val service = ClickAccessibilityService.instance
        if (service == null) {
            lastError = "請先開啟無障礙服務"
            return false
        }
        isRunning = true
        isPaused = false
        lastError = ""
        pointIndex = 0
        pingDir = 1
        currentPointIndex = 0
        remaining = count
        startTimeMs = System.currentTimeMillis()
        updateRemaining()
        gestureInFlight = false
        val gen = ++generation
        if (clearLogOnStart) log.clear()
        addLog("開始")
        appContext?.let { ctx ->
            ClickNotification.showRunning(ctx, "執行中")
            if (vibrateOnStart) {
                val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                vib?.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        40,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
            if (showToast) {
                android.widget.Toast.makeText(ctx, "開始", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        val launch: () -> Unit = { scheduleTick(gen) }
        if (startDelayMs > 0) handler.postDelayed(launch, startDelayMs) else launch()
        return true
    }

    private fun scheduleTick(gen: Int) {
        if (gen != generation || !isRunning) return
        handler.post { tick(gen) }
    }

    private fun scheduleAfter(gen: Int, delayMs: Long) {
        if (gen != generation || !isRunning) return
        handler.postDelayed({ tick(gen) }, delayMs.coerceAtLeast(0))
    }

    private fun computeDelay(): Long {
        var delay = if (randomInterval) intervalMs + kotlin.random.Random.nextLong(-30, 31) else intervalMs
        if (delay < 10) delay = 10
        if (jitterInterval) delay += kotlin.random.Random.nextLong(-10, 11)
        if (invertInterval && totalClicks > 0) delay = (delay * 0.95).toLong().coerceAtLeast(20)
        if (decelerate && totalClicks > 0) delay = (delay * 1.05).toLong()
        if (slowStart && totalClicks < 5) delay *= 2
        if (antiDetect) delay += kotlin.random.Random.nextLong(0, 80)
        val minDelay = if (safeMode) 100L else if (maxCps > 0) 1000L / maxCps else 20L
        return delay.coerceAtLeast(minDelay).coerceAtMost(10000)
    }

    private fun transformPoint(rawX: Float, rawY: Float): Pair<Float, Float> {
        var x = rawX * scale + offsetX
        var y = rawY * scale + offsetY
        if (mirrorX) appContext?.resources?.displayMetrics?.let { x = it.widthPixels - x }
        if (mirrorY) appContext?.resources?.displayMetrics?.let { y = it.heightPixels - y }
        if (clampToScreen) {
            appContext?.resources?.displayMetrics?.let {
                x = x.coerceIn(0f, it.widthPixels.toFloat())
                y = y.coerceIn(0f, it.heightPixels.toFloat())
            }
        }
        if (randomOffset || humanize) {
            val range = if (humanize) 3 else 8
            x += kotlin.random.Random.nextInt(-range, range + 1).toFloat()
            y += kotlin.random.Random.nextInt(-range, range + 1).toFloat()
        }
        return x to y
    }

    private fun advancePointIndex(size: Int) {
        if (size <= 1) return
        if (pingPong) {
            pointIndex += pingDir
            if (pointIndex >= size || pointIndex < 0) {
                pingDir = -pingDir
                pointIndex = (pointIndex + pingDir).coerceIn(0, size - 1)
            }
        } else {
            pointIndex = (pointIndex + 1) % size
        }
        currentPointIndex = pointIndex.coerceIn(0, size - 1)
    }

    private fun tick(gen: Int) {
        if (gen != generation || !isRunning) return
        if (gestureInFlight) return
        if (isPaused) {
            scheduleAfter(gen, 200)
            return
        }
        if (count >= 0 && remaining <= 0) {
            stop(); return
        }
        if (maxMinutes > 0 && elapsedSec() >= maxMinutes * 60L) {
            stop(); return
        }
        if (stopAtTotal > 0 && totalClicks >= stopAtTotal) {
            stop(); return
        }
        val service = ClickAccessibilityService.instance
        if (service == null) {
            lastError = "無障礙服務已斷開"
            stop()
            return
        }
        if (onlyEven && totalClicks % 2 == 1) {
            scheduleAfter(gen, intervalMs); return
        }
        if (onlyOdd && totalClicks % 2 == 0) {
            scheduleAfter(gen, intervalMs); return
        }
        if (skipEvery > 0 && totalClicks % skipEvery == 0 && totalClicks > 0) {
            addLog("跳過")
            scheduleAfter(gen, intervalMs)
            return
        }
        if (pauseEvery > 0 && totalClicks % pauseEvery == 0 && totalClicks > 0) {
            isPaused = true
            addLog("自動暫停")
            handler.postDelayed({
                isPaused = false
                if (isRunning && gen == generation) scheduleTick(gen)
            }, 500)
            return
        }
        if (points.isEmpty()) {
            stop(); return
        }

        if (mode == "滑動") {
            runSwipe(gen, service)
        } else {
            runClick(gen, service)
        }
    }

    private fun runClick(gen: Int, service: ClickAccessibilityService) {
        val base = if (mode == "多點" && points.size > 1) points.toList() else listOf(points.first())
        val usePoints = if (reversePoints) base.reversed() else base
        if (usePoints.isEmpty()) {
            stop(); return
        }
        val idx = (
            if (randomPoint && usePoints.size > 1) kotlin.random.Random.nextInt(usePoints.size)
            else pointIndex % usePoints.size.coerceAtLeast(1)
            ).coerceIn(0, usePoints.lastIndex)
        val raw = usePoints.getOrElse(idx) { usePoints.first() }
        val (x, y) = transformPoint(raw.first, raw.second)
        val dur = if (longPress) durationMs * 4 else durationMs

        gestureInFlight = true
        val dispatched = service.performClick(x, y, dur.coerceAtLeast(10)) { ok ->
            if (gen != generation) return@performClick
            if (!ok) {
                gestureInFlight = false
                if (stopOnError) {
                    lastError = "手勢失敗"
                    addLog("手勢失敗，停止")
                    stop()
                } else {
                    scheduleAfter(gen, computeDelay())
                }
                return@performClick
            }
            fun afterPrimary() {
                if (gen != generation || !isRunning) {
                    gestureInFlight = false
                    return
                }
                totalClicks++
                if (soundOnClick) {
                    try {
                        toneGen().startTone(ToneGenerator.TONE_PROP_BEEP, 30)
                    } catch (_: Exception) {
                    }
                }
                if (logClicks && (logEvery <= 1 || totalClicks % logEvery == 0)) {
                    addLog("點擊 ${x.toInt()},${y.toInt()}")
                }
                if (debugLog && totalClicks % 10 == 0) {
                    addLog("idx=$pointIndex x=${x.toInt()} y=${y.toInt()}")
                }
                if (usePoints.size > 1 && !randomPoint) advancePointIndex(usePoints.size)
                if (count >= 0 && !(warmupClicks > 0 && totalClicks <= warmupClicks)) {
                    remaining = (remaining - 1).coerceAtLeast(0)
                }
                updateRemaining()
                notifyProgress()
                runBurstThenContinue(gen, service, x, y, dur, burstLeft = burstCount - 1)
            }
            if (doubleTap) {
                service.performClick(x, y, dur.coerceAtLeast(10)) { _ ->
                    if (gen != generation) return@performClick
                    afterPrimary()
                }
            } else {
                afterPrimary()
            }
        }
        if (!dispatched) {
            gestureInFlight = false
            if (stopOnError) {
                lastError = "手勢失敗"
                addLog("手勢失敗，停止")
                stop()
            } else {
                scheduleAfter(gen, computeDelay())
            }
        }
    }

    private fun runBurstThenContinue(
        gen: Int,
        service: ClickAccessibilityService,
        x: Float,
        y: Float,
        dur: Long,
        burstLeft: Int
    ) {
        if (gen != generation || !isRunning) {
            gestureInFlight = false
            return
        }
        if (burstLeft <= 0) {
            gestureInFlight = false
            val pause = if (burstCount > 1 && burstPause > 0) burstPause else computeDelay()
            scheduleAfter(gen, pause)
            return
        }
        val ok = service.performClick(x, y, dur.coerceAtLeast(10)) { success ->
            if (gen != generation) return@performClick
            if (success) totalClicks++
            runBurstThenContinue(gen, service, x, y, dur, burstLeft - 1)
        }
        if (!ok) {
            gestureInFlight = false
            scheduleAfter(gen, computeDelay())
        }
    }

    private fun runSwipe(gen: Int, service: ClickAccessibilityService) {
        val pts = if (reversePoints) points.toList().reversed() else points.toList()
        if (pts.size < 2) {
            lastError = "滑動模式至少需要 2 個點"
            stop()
            return
        }
        val i0 = pointIndex.coerceIn(0, pts.lastIndex - 1)
        val i1 = (i0 + 1).coerceAtMost(pts.lastIndex)
        val (x1, y1) = transformPoint(pts[i0].first, pts[i0].second)
        val (x2, y2) = transformPoint(pts[i1].first, pts[i1].second)
        val swipeDur = durationMs.coerceAtLeast(80)

        gestureInFlight = true
        val dispatched = service.performSwipe(x1, y1, x2, y2, swipeDur) { ok ->
            if (gen != generation) return@performSwipe
            gestureInFlight = false
            if (!ok) {
                if (stopOnError) {
                    lastError = "手勢失敗"
                    addLog("手勢失敗，停止")
                    stop()
                } else {
                    scheduleAfter(gen, computeDelay())
                }
                return@performSwipe
            }
            totalClicks++
            if (soundOnClick) {
                try {
                    toneGen().startTone(ToneGenerator.TONE_PROP_BEEP, 30)
                } catch (_: Exception) {
                }
            }
            if (logClicks && (logEvery <= 1 || totalClicks % logEvery == 0)) {
                addLog("滑動 ${x1.toInt()},${y1.toInt()}→${x2.toInt()},${y2.toInt()}")
            }
            if (pingPong) {
                pointIndex += pingDir
                if (pointIndex >= pts.size - 1 || pointIndex < 0) {
                    pingDir = -pingDir
                    pointIndex = (pointIndex + pingDir).coerceIn(0, pts.size - 2)
                }
            } else {
                pointIndex = (pointIndex + 1) % (pts.size - 1).coerceAtLeast(1)
            }
            currentPointIndex = pointIndex.coerceIn(0, pts.lastIndex)
            if (count >= 0 && !(warmupClicks > 0 && totalClicks <= warmupClicks)) {
                remaining = (remaining - 1).coerceAtLeast(0)
            }
            updateRemaining()
            notifyProgress()
            scheduleAfter(gen, computeDelay())
        }
        if (!dispatched) {
            gestureInFlight = false
            if (stopOnError) {
                lastError = "手勢失敗"
                addLog("手勢失敗，停止")
                stop()
            } else {
                scheduleAfter(gen, computeDelay())
            }
        }
    }

    private fun notifyProgress() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyMs > 500) {
            lastNotifyMs = now
            appContext?.let { ClickNotification.showRunning(it, "剩餘 $remainingDisplay") }
        }
    }

    fun stop() {
        if (isRunning) addLog("停止")
        generation++
        isRunning = false
        isPaused = false
        gestureInFlight = false
        handler.removeCallbacksAndMessages(null)
        if (endBeep) {
            try {
                toneGen().startTone(ToneGenerator.TONE_PROP_ACK, 80)
            } catch (_: Exception) {
            }
        }
        appContext?.let { ClickNotification.cancel(it) }
        // keep tone for reuse across sessions; release only when process dies
    }

    fun releaseResources() {
        releaseTone()
    }

    fun toggle(): Boolean = if (isRunning) {
        stop(); false
    } else start()
}
