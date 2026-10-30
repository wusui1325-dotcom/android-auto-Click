package com.wusui.androidautoclick

import android.content.Context
import android.content.SharedPreferences

object SettingsStore {
    private const val PREF = "autoclick"
    private const val KEY_POINTS = "points"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_SEQ_CURRENT = "seq_current"
    private const val KEY_SEQ_SAVED = "seq_saved"
    private const val KEY_SHOW_MARKERS = "showMarkers"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        load()
    }

    private fun p(): SharedPreferences? = prefs

    fun load() {
        val prefs = p() ?: return
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
        ClickController.showMarkers = prefs.getBoolean(KEY_SHOW_MARKERS, true)
        ClickController.showCoordsOnOverlay = prefs.getBoolean("showCoords", false)
        loadPoints(prefs)
        loadFavorites(prefs)
        loadSequences(prefs)
    }

    fun save() {
        val prefs = p() ?: return
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
            .putBoolean(KEY_SHOW_MARKERS, ClickController.showMarkers)
            .putBoolean("showCoords", ClickController.showCoordsOnOverlay)
            .apply()
        savePoints()
        saveFavorites()
        saveSequences()
    }

    fun savePoints() {
        val prefs = p() ?: return
        val encoded = ClickController.points.joinToString(";") { "${it.first},${it.second}" }
        prefs.edit().putString(KEY_POINTS, encoded).apply()
    }

    fun saveFavorites() {
        val prefs = p() ?: return
        val encoded = ClickController.favoritePoints.joinToString(";") { "${it.first},${it.second}" }
        prefs.edit().putString(KEY_FAVORITES, encoded).apply()
    }

    fun saveSequences() {
        val prefs = p() ?: return
        prefs.edit()
            .putString(KEY_SEQ_CURRENT, SequenceRecorder.exportText())
            .putString(KEY_SEQ_SAVED, encodeSavedSequences())
            .apply()
    }

    private fun loadPoints(prefs: SharedPreferences) {
        val raw = prefs.getString(KEY_POINTS, null) ?: return
        val parsed = parsePoints(raw)
        if (parsed.isEmpty()) return
        ClickController.points.clear()
        ClickController.points.addAll(parsed)
    }

    private fun loadFavorites(prefs: SharedPreferences) {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return
        ClickController.favoritePoints.clear()
        ClickController.favoritePoints.addAll(parsePoints(raw))
    }

    private fun loadSequences(prefs: SharedPreferences) {
        // persist=false：避免 load 途中把尚未還原的 saved map 寫回成空
        val current = prefs.getString(KEY_SEQ_CURRENT, null)
        if (!current.isNullOrBlank()) {
            SequenceRecorder.importText(current, persist = false)
        }
        val saved = prefs.getString(KEY_SEQ_SAVED, null)
        if (!saved.isNullOrBlank()) {
            SequenceRecorder.loadSavedFromEncoded(saved)
        }
    }

    private fun parsePoints(raw: String): List<Pair<Float, Float>> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val xy = part.split(",")
            if (xy.size < 2) return@mapNotNull null
            val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
            val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
            x to y
        }
    }

    /** name\nsteps... separated by \n====\n */
    private fun encodeSavedSequences(): String {
        return SequenceRecorder.saved.entries.joinToString("\n====\n") { (name, steps) ->
            buildString {
                appendLine(name)
                steps.forEach { s ->
                    appendLine(
                        when (s.type) {
                            "click" -> "click ${s.x.toInt()} ${s.y.toInt()} ${s.delayMs}"
                            "wait" -> "wait ${s.delayMs}"
                            "swipe" -> "swipe ${s.x.toInt()} ${s.y.toInt()} ${s.x2.toInt()} ${s.y2.toInt()} ${s.delayMs}"
                            else -> s.type
                        }
                    )
                }
            }.trimEnd()
        }
    }

    fun exportText(): String = buildString {
        appendLine("interval=${ClickController.intervalMs}")
        appendLine("duration=${ClickController.durationMs}")
        appendLine("count=${ClickController.count}")
        appendLine("mode=${ClickController.mode}")
        appendLine("maxCps=${ClickController.maxCps}")
        appendLine("startDelay=${ClickController.startDelayMs}")
        appendLine("loops=${SequenceRecorder.replayLoops}")
        appendLine("points=${ClickController.points.joinToString(";") { "${it.first},${it.second}" }}")
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
                "points" -> {
                    val parsed = parsePoints(p[1])
                    if (parsed.isNotEmpty()) {
                        ClickController.points.clear()
                        ClickController.points.addAll(parsed)
                    }
                }
            }
        }
        save()
    }
}
