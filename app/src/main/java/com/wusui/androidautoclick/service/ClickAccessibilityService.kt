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
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!ClickEngine.status.value.recording) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val node = event.source ?: return
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        // AccessibilityNodeInfo.recycle() is deprecated/no-op on modern APIs — do not call.
        ClickEngine.recordClick(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    override fun onInterrupt() {}
}
