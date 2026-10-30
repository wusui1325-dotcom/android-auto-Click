package com.wusui.androidautoclick.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.wusui.androidautoclick.engine.ClickEngine

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
            ClickEngine.stop()
            ClickEngine.stopRecording()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            ClickEngine.stop()
            ClickEngine.stopRecording()
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!ClickEngine.status.value.recording) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            type != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        ) return
        val node = event.source ?: return
        try {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.isEmpty) return
            ClickEngine.onAccessibilityClick(rect.centerX().toFloat(), rect.centerY().toFloat())
        } finally {
            // AccessibilityNodeInfo.recycle() deprecated/no-op on modern APIs — do not call.
        }
    }

    override fun onInterrupt() {}
}
