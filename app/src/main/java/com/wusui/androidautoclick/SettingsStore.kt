package com.wusui.androidautoclick

import android.content.Context
import android.content.SharedPreferences

object SettingsStore {
    private const val PREF = "autoclick"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        ClickController.intervalMs = prefs.getLong("interval", 200)
        ClickController.durationMs = prefs.getLong("duration", 50)
        ClickController.count = prefs.getInt("count", -1)
        ClickController.mode = prefs.getString("mode", "單點") ?: "單點"
        ClickController.randomOffset = prefs.getBoolean("randOff", false)
        ClickController.randomInterval = prefs.getBoolean("randInt", false)
        ClickController.antiDetect = prefs.getBoolean("anti", false)
        ClickController.startDelayMs = prefs.getLong("startDelay", 0)
        SequenceRecorder.replayLoops = prefs.getInt("loops", 1)
    }

    fun save() {
        prefs.edit()
            .putLong("interval", ClickController.intervalMs)
            .putLong("duration", ClickController.durationMs)
            .putInt("count", ClickController.count)
            .putString("mode", ClickController.mode)
            .putBoolean("randOff", ClickController.randomOffset)
            .putBoolean("randInt", ClickController.randomInterval)
            .putBoolean("anti", ClickController.antiDetect)
            .putLong("startDelay", ClickController.startDelayMs)
            .putInt("loops", SequenceRecorder.replayLoops)
            .apply()
    }
}
