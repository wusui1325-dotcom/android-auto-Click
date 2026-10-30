package com.wusui.androidautoclick

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.wusui.androidautoclick.service.ClickAccessibilityService

object ClickController {

    var appContext: Context? = null
    var isRunning: Boolean = false
        private set
    var isPaused: Boolean = false
        private set
    var startTimeMs: Long = 0
        private set
    var totalClicks: Int = 0
        private set
    var lastError: String = ""
        private set
    var remainingDisplay: String = ""
        private set
    var currentPointIndex: Int = 0
        private set

    var intervalMs: Long = 200
    var durationMs: Long = 50
    var count: Int = -1
    var mode: String = "單點"
    var points: MutableList<Pair<Float, Float>> = mutableListOf(540f to 960f)
    var startDelayMs: Long = 0
    var maxCps: Int = 20
    var maxMinutes: Int = 0
    var randomOffset: Boolean = false
    var randomInterval: Boolean = false
    var antiDetect: Boolean = false
    var humanize: Boolean = false
    var burstCount: Int = 1
    var burstPause: Long = 0
    var warmupClicks: Int = 0
    var slowStart: Boolean = false
    var invertInterval: Boolean = false
    var decelerate: Boolean = false
    var skipEvery: Int = 0
    var doubleTap: Boolean = false
    var longPress: Boolean = false
    var jitterInterval: Boolean = false
    var onlyEven: Boolean = false
    var onlyOdd: Boolean = false
    var reversePoints: Boolean = false
    var pingPong: Boolean = false
    var randomPoint: Boolean = false
    var clampToScreen: Boolean = true
    var logClicks: Boolean = true
    var logEvery: Int = 1
    var notifyEvery: Int = 0
    var stopAtTotal: Int = 0
    var pauseEvery: Int = 0
    var safeMode: Boolean = false
    var debugLog: Boolean = false
    var vibrateOnStart: Boolean = false
    var showToast: Boolean = false
    var endBeep: Boolean = false
    var autoHideOverlay: Boolean = false
    var confirmStop: Boolean = false
    var mirrorX: Boolean = false
    var mirrorY: Boolean = false
    var offsetX: Float = 0f
    var offsetY: Float = 0f
    var scale: Float = 1f
    var showCoordsOnOverlay: Boolean = false
    var clearLogOnStart: Boolean = false
    var clickOnDown: Boolean = false
    var keepScreenOn: Boolean = false
    var soundOnClick: Boolean = false
    var stopOnError: Boolean = true

    val log = mutableListOf<String>()
    val favoritePoints = mutableListOf<Pair<Float, Float>>()

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var remaining: Int = 0
    private var pointIndex = 0
    private var pingDir = 1
    private var lastNotifyMs = 0L

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun elapsedSec(): Long = if (isRunning) (System.currentTimeMillis() - startTimeMs) / 1000 else 0

    fun pause() { isPaused = true; addLog("暫停") }
    fun resume() { isPaused = false; addLog("繼續") }
    fun clearError() { lastError = "" }
    fun resetTotal() { totalClicks = 0 }

    fun addPoint(x: Float, y: Float) { points.add(x to y) }
    fun clearPoints() { points.clear(); points.add(540f to 960f) }
    fun duplicateLastPoint() { points.lastOrNull()?.let { points.add(it) } }
    fun nudgeLastPoint(dx: Float, dy: Float) {
        if (points.isEmpty()) return
        val last = points.last()
        points[points.lastIndex] = (last.first + dx) to (last.second + dy)
    }
    fun saveFavorite() { points.lastOrNull()?.let { favoritePoints.add(it) } }
    fun loadFavorite(index: Int) {
        favoritePoints.getOrNull(index)?.let {
            if (points.isEmpty()) points.add(it) else points[points.lastIndex] = it
        }
    }
    fun clearFavorites() { favoritePoints.clear() }

    fun resetDefaults() {
        intervalMs = 200; durationMs = 50; count = -1; mode = "單點"
        randomOffset = false; randomInterval = false; antiDetect = false
        startDelayMs = 0; points.clear(); points.add(540f to 960f)
    }

    private fun addLog(msg: String) {
        log.add(0, msg)
        if (log.size > 20) log.removeAt(log.lastIndex)
    }

    private fun updateRemaining() {
        remainingDisplay = if (count < 0) "∞" else remaining.coerceAtLeast(0).toString()
    }

    fun start(): Boolean {
        if (isRunning) return true
        if (points.isEmpty()) {
            lastError = "沒有點擊座標"
            return false
        }
        val service = ClickAccessibilityService.instance ?: return false
        isRunning = true
        lastError = ""
        remaining = count
        pointIndex = 0
        startTimeMs = System.currentTimeMillis()
        updateRemaining()
        if (clearLogOnStart) log.clear()
        addLog("開始")
        appContext?.let { ctx ->
            ClickNotification.showRunning(ctx, "執行中")
            if (vibrateOnStart) {
                val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                vib?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
            if (showToast) android.widget.Toast.makeText(ctx, "開始", android.widget.Toast.LENGTH_SHORT).show()
        }

        runnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (isPaused) { handler.postDelayed(this, 200); return }
                if (count >= 0 && remaining <= 0) { stop(); return }
                if (maxMinutes > 0 && elapsedSec() >= maxMinutes * 60L) { stop(); return }
                if (stopAtTotal > 0 && totalClicks >= stopAtTotal) { stop(); return }
                if (ClickAccessibilityService.instance == null) { lastError = "無障礙服務已斷開"; stop(); return }
                if (onlyEven && totalClicks % 2 == 1) { handler.postDelayed(this, intervalMs); return }
                if (onlyOdd && totalClicks % 2 == 0) { handler.postDelayed(this, intervalMs); return }
                if (skipEvery > 0 && totalClicks % skipEvery == 0 && totalClicks > 0) {
                    addLog("跳過"); handler.postDelayed(this, intervalMs); return
                }
                if (pauseEvery > 0 && totalClicks % pauseEvery == 0 && totalClicks > 0) {
                    isPaused = true; addLog("自動暫停")
                    handler.postDelayed({ isPaused = false; if (isRunning) handler.post(this) }, 500)
                    return
                }

                if (points.isEmpty()) { stop(); return }
                val base = if (mode == "多點" && points.size > 1) points.toList() else listOf(points.first())
                val usePoints = if (reversePoints) base.reversed() else base
                if (usePoints.isEmpty()) { stop(); return }
                val idx = (if (randomPoint && usePoints.size > 1) kotlin.random.Random.nextInt(usePoints.size) else pointIndex % usePoints.size.coerceAtLeast(1)).coerceIn(0, usePoints.lastIndex)
                var (x, y) = usePoints.getOrElse(idx) { usePoints.first() }
                x = x * scale + offsetX
                y = y * scale + offsetY
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

                val dur = if (longPress) durationMs * 4 else durationMs
                val ok = service.performClick(x, y, dur.coerceAtLeast(10))
                if (doubleTap) service.performClick(x, y, dur)
                if (!ok && stopOnError) { lastError = "手勢失敗"; addLog("手勢失敗，停止"); stop(); return }
                totalClicks++
                if (burstCount > 1) {
                    repeat(burstCount - 1) { service.performClick(x, y, dur.coerceAtLeast(10)); totalClicks++ }
                    if (burstPause > 0 && isRunning) { handler.postDelayed(this, burstPause); return }
                }
                if (soundOnClick) {
                    android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 40)
                        .startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 30)
                }
                if (logClicks && (logEvery <= 1 || totalClicks % logEvery == 0)) addLog("點擊 ${x.toInt()},${y.toInt()}")
                if (debugLog && totalClicks % 10 == 0) addLog("idx=$pointIndex x=${x.toInt()} y=${y.toInt()}")

                if (usePoints.size > 1 && !randomPoint) {
                    if (pingPong) {
                        pointIndex += pingDir
                        if (pointIndex >= usePoints.size || pointIndex < 0) {
                            pingDir = -pingDir
                            pointIndex = (pointIndex + pingDir).coerceIn(0, usePoints.size - 1)
                        }
                    } else pointIndex = (pointIndex + 1) % usePoints.size
                    currentPointIndex = pointIndex
                }
                if (count >= 0 && !(warmupClicks > 0 && totalClicks <= warmupClicks)) remaining--
                updateRemaining()

                val now = System.currentTimeMillis()
                if (now - lastNotifyMs > 500) {
                    lastNotifyMs = now
                    appContext?.let { ClickNotification.showRunning(it, "剩餘 $remainingDisplay") }
                }

                if (isRunning) {
                    var delay = if (randomInterval) intervalMs + kotlin.random.Random.nextLong(-30, 31) else intervalMs
                    if (delay < 10) delay = 10
                    if (jitterInterval) delay += kotlin.random.Random.nextLong(-10, 11)
                    if (invertInterval && totalClicks > 0) delay = (delay * 0.95).toLong().coerceAtLeast(20)
                    if (decelerate && totalClicks > 0) delay = (delay * 1.05).toLong()
                    if (slowStart && totalClicks < 5) delay *= 2
                    if (antiDetect) delay += kotlin.random.Random.nextLong(0, 80)
                    val minDelay = if (safeMode) 100L else if (maxCps > 0) 1000L / maxCps else 20L
                    handler.postDelayed(this, delay.coerceAtLeast(minDelay))
                }
            }
        }
        val launch: () -> Unit = { runnable?.let { handler.post(it) } }
        if (startDelayMs > 0) handler.postDelayed(launch, startDelayMs) else launch()
        return true
    }

    fun stop() {
        if (isRunning) addLog("停止")
        isRunning = false
        isPaused = false
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        if (endBeep) {
            android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60)
                .startTone(android.media.ToneGenerator.TONE_PROP_ACK, 80)
        }
        appContext?.let { ClickNotification.cancel(it) }
    }

    fun toggle(): Boolean = if (isRunning) { stop(); false } else start()
}
