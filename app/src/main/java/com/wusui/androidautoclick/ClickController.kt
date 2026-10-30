package com.wusui.androidautoclick

import com.wusui.androidautoclick.service.ClickAccessibilityService
import android.os.Handler
import android.os.Looper

/**
 * 全域點擊控制器。
 * UI 與懸浮窗共用同一組參數與執行狀態。
 */
object ClickController {

    var intervalMs: Long = 200
    var durationMs: Long = 50
    var count: Int = -1 // -1 = 無限
    var points: List<Pair<Float, Float>> = listOf(540f to 960f)

    var isRunning: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var remaining: Int = 0
    private var pointIndex = 0

    fun start() {
        if (isRunning) return
        val service = ClickAccessibilityService.instance ?: return
        isRunning = true
        remaining = count
        pointIndex = 0

        runnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (count >= 0 && remaining <= 0) {
                    stop()
                    return
                }

                val (x, y) = points.getOrElse(pointIndex) { points.first() }
                service.performClick(x, y, durationMs)

                if (points.size > 1) {
                    pointIndex = (pointIndex + 1) % points.size
                }
                if (count >= 0) remaining--

                if (isRunning) {
                    handler.postDelayed(this, intervalMs)
                }
            }
        }
        handler.post(runnable!!)
    }

    fun stop() {
        isRunning = false
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    fun toggle() {
        if (isRunning) stop() else start()
    }
}
