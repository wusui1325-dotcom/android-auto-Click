package com.wusui.androidautoclick.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper

/**
 * 自動點擊核心服務。
 * 必須由使用者在系統「無障礙設定」中手動開啟。
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ClickAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 殼階段暫不處理事件，後續可監聽特定視窗
    }

    override fun onInterrupt() {}

    /**
     * 在指定座標執行單次點擊。
     * @return 是否成功送出手勢
     */
    fun performClick(x: Float, y: Float, durationMs: Long = 50): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * 連續點擊（簡易版，後續可改為可取消的協程）。
     */
    fun performContinuousClick(
        x: Float,
        y: Float,
        intervalMs: Long,
        count: Int,
        onComplete: (() -> Unit)? = null
    ) {
        val handler = Handler(Looper.getMainLooper())
        var remaining = count
        val runnable = object : Runnable {
            override fun run() {
                if (remaining <= 0) {
                    onComplete?.invoke()
                    return
                }
                performClick(x, y)
                remaining--
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.post(runnable)
    }
}
