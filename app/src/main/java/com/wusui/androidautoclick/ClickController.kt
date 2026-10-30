package com.wusui.androidautoclick

import com.wusui.androidautoclick.service.ClickAccessibilityService
import android.os.Handler
import android.os.Looper
import android.content.Context

/**
 * 全域點擊控制器。
 * UI 與懸浮窗共用同一組參數與執行狀態。
 */
object ClickController {

    var appContext: Context? = null
    var maxMinutes: Int = 0 // 0 = 無限制
    var antiDetect: Boolean = false
    var randomOffset: Boolean = false
    var randomInterval: Boolean = false
    var mode: String = "單點"
    var durationMs: Long = 50
    var count: Int = -1 // -1 = 無限
    var points: MutableList<Pair<Float, Float>> = mutableListOf(540f to 960f)

    var isPaused: Boolean = false
        private set

    fun pause() {
        isPaused = true
        addLog("暫停")
    }

    fun resume() {
        isPaused = false
        addLog("繼續")
    }

    fun elapsedSec(): Long = if (isRunning) (System.currentTimeMillis() - startTimeMs) / 1000 else 0
    var remainingDisplay: String = ""
        private set
    var currentPointIndex: Int = 0
        private set

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun resetTotal() {
        totalClicks = 0
    }
        intervalMs = 200
        durationMs = 50
        count = -1
        mode = "單點"
        randomOffset = false
        randomInterval = false
        antiDetect = false
        startDelayMs = 0
        points.clear()
        points.add(540f to 960f)
    }

    fun nudgeLastPoint(dx: Float, dy: Float) {
        if (points.isEmpty()) return
        val last = points.last()
        points[points.lastIndex] = (last.first + dx) to (last.second + dy)
    }

    val log = mutableListOf<String>()

    private fun addLog(msg: String) {
        log.add(0, msg)
        if (log.size > 20) log.removeAt(log.lastIndex)
    }

    private fun updateRemaining() {
        remainingDisplay = if (count < 0) "∞" else remaining.coerceAtLeast(0).toString()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var remaining: Int = 0
    private var pointIndex = 0

    fun start(): Boolean {
        if (isRunning) return true
        val service = ClickAccessibilityService.instance ?: return false
        isRunning = true
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
        }

        runnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (isPaused) {
                    handler.postDelayed(this, 200)
                    return
                }
                if (maxMinutes > 0 && elapsedSec() >= maxMinutes * 60L) {
                    stop()
                    return
                }
                if (count >= 0 && remaining <= 0) {
                    stop()
                    return
                }

                val usePoints = if (mode == "多點" && points.size > 1) points else listOf(points.first())
                var (x, y) = usePoints.getOrElse(pointIndex % usePoints.size) { usePoints.first() }
                if (randomOffset) {
                    x += (kotlin.random.Random.nextInt(-8, 9)).toFloat()
                    y += (kotlin.random.Random.nextInt(-8, 9)).toFloat()
                }
                service.performClick(x, y, durationMs)
                totalClicks++
                if (soundOnClick) {
                    android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 40)
                        .startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 30)
                }
                addLog("點擊 ${x.toInt()},${y.toInt()}")

                if (usePoints.size > 1) {
                    pointIndex = (pointIndex + 1) % usePoints.size
                    currentPointIndex = pointIndex
                }
                if (count >= 0) remaining--
                updateRemaining()
                appContext?.let { ClickNotification.showRunning(it, "剩餘 ${remainingDisplay}") }

                if (isRunning) {
                    var delay = if (randomInterval) intervalMs + kotlin.random.Random.nextLong(-30, 31) else intervalMs
                    if (antiDetect) delay += kotlin.random.Random.nextLong(0, 80)
                    val minDelay = if (maxCps > 0) 1000L / maxCps else 20L
                    handler.postDelayed(this, delay.coerceAtLeast(minDelay))
                }
            }
        }
        val launch = { handler.post(runnable!!) }
        if (startDelayMs > 0) handler.postDelayed(launch, startDelayMs) else launch()
        return true
    }

    fun stop() {
        if (isRunning) addLog("停止")
        isRunning = false
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        appContext?.let { ClickNotification.cancel(it) }
    }

    fun toggle(): Boolean {
        return if (isRunning) {
            stop()
            false
        } else {
            start()
        }
    }
}
