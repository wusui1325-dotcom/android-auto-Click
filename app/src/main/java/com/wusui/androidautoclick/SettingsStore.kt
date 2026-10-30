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
        ClickController.maxCps = prefs.getInt("maxCps", 20)
        ClickController.maxMinutes = prefs.getInt("maxMin", 0)
        SettingsColors.isDark = prefs.getBoolean("dark", true)
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
            .putInt("maxCps", ClickController.maxCps)
            .putInt("maxMin", ClickController.maxMinutes)
            .putBoolean("dark", SettingsColors.isDark)
            .putInt("loops", SequenceRecorder.replayLoops)
            .apply()
    }

    fun exportText(): String = buildString {
        appendLine("interval=${ClickController.intervalMs}")
        appendLine("duration=${ClickController.durationMs}")
        appendLine("count=${ClickController.count}")
        appendLine("mode=${ClickController.mode}")
        appendLine("maxCps=${ClickController.maxCps}")
        appendLine("startDelay=${ClickController.startDelayMs}")
        appendLine("loops=${SequenceRecorder.replayLoops}")
    }

    fun importText(text: String) {
        text.lines().forEach { line ->
            val p = line.split("=", limit = 2)
            if (p.size != 2) return@forEach
            when (p[0]) {
                "interval" -> ClickController.intervalMs = p[1].toLongOrNull() ?: ClickController.intervalMs
                "duration" -> ClickController.durationMs = p[1].toLongOrNull() ?: ClickController.durationMs
                "count" -> ClickController.count = p[1].toIntOrNull() ?: ClickController.count
                "mode" -> ClickController.mode = p[1]
                "maxCps" -> ClickController.maxCps = p[1].toIntOrNull() ?: ClickController.maxCps
                "startDelay" -> ClickController.startDelayMs = p[1].toLongOrNull() ?: ClickController.startDelayMs
                "loops" -> SequenceRecorder.replayLoops = p[1].toIntOrNull() ?: SequenceRecorder.replayLoops
            }
        }
        save()
    }
}
