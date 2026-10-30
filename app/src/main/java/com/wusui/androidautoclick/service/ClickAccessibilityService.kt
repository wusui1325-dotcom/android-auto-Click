package com.wusui.androidautoclick.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.wusui.androidautoclick.SequenceRecorder

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

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !SequenceRecorder.isRecording) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val node = event.source ?: return
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val x = rect.centerX().toFloat()
            val y = rect.centerY().toFloat()
            SequenceRecorder.addClick(x, y, com.wusui.androidautoclick.ClickController.intervalMs)
            node.recycle()
        }
    }

    override fun onInterrupt() {}

    /**
     * 在指定座標執行單次點擊；完成／取消後呼叫 onDone（主線程）。
     * @return 是否成功送出手勢（不等於手勢已完成）
     */
    fun performClick(
        x: Float,
        y: Float,
        durationMs: Long = 50,
        onDone: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (durationMs < 1) {
            onDone?.invoke(false)
            return false
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchWithCallback(gesture, onDone)
    }

    fun performSwipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = 300,
        onDone: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (durationMs < 1) {
            onDone?.invoke(false)
            return false
        }
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchWithCallback(gesture, onDone)
    }

    private fun dispatchWithCallback(
        gesture: GestureDescription,
        onDone: ((Boolean) -> Unit)?
    ): Boolean {
        if (onDone == null) {
            return dispatchGesture(gesture, null, null)
        }
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                mainHandler.post { onDone(true) }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                mainHandler.post { onDone(false) }
            }
        }
        return dispatchGesture(gesture, cb, null)
    }
}
