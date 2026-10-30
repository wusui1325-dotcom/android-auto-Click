package com.wusui.androidautoclick.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wusui.androidautoclick.engine.ClickEngine

class EngineActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> ClickEngine.stop()
            ACTION_TOGGLE -> ClickEngine.toggle()
        }
    }

    companion object {
        const val ACTION_STOP = "com.wusui.androidautoclick.action.STOP"
        const val ACTION_TOGGLE = "com.wusui.androidautoclick.action.TOGGLE"
    }
}
