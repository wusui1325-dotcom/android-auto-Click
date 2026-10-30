package com.wusui.androidautoclick.data

import android.content.Context
import android.content.SharedPreferences
import com.wusui.androidautoclick.engine.ClickMode
import com.wusui.androidautoclick.engine.ClickPoint
import com.wusui.androidautoclick.engine.ClickPreset
import com.wusui.androidautoclick.engine.EngineConfig
import com.wusui.androidautoclick.engine.RunStats
import com.wusui.androidautoclick.engine.SequenceStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * Single source of truth for all persisted settings, points, sequences, and stats.
 */
object SettingsRepository {

    private const val PREF = "autoclick_v2"
    private const val EXPORT_MAGIC = "AutoClickExport"
    private const val EXPORT_VER = 1
    private var prefs: SharedPreferences? = null
    var appContext: Context? = null
        private set

    private var sequenceBatch = false

    private val _config = MutableStateFlow(EngineConfig())
    val config: StateFlow<EngineConfig> = _config.asStateFlow()

    private val _points = MutableStateFlow(listOf(ClickPoint(540f, 960f)))
    val points: StateFlow<List<ClickPoint>> = _points.asStateFlow()

    private val _sequenceSteps = MutableStateFlow<List<SequenceStep>>(emptyList())
    val sequenceSteps: StateFlow<List<SequenceStep>> = _sequenceSteps.asStateFlow()

    private val _savedSequences = MutableStateFlow<Map<String, List<SequenceStep>>>(emptyMap())
    val savedSequences: StateFlow<Map<String, List<SequenceStep>>> = _savedSequences.asStateFlow()

    private val _stats = MutableStateFlow(RunStats())
    val stats: StateFlow<RunStats> = _stats.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        val p = prefs ?: return
        _config.value = EngineConfig(
            intervalMs = p.getLong("interval", 200),
            durationMs = p.getLong("duration", 50),
            count = p.getInt("count", -1),
            mode = ClickMode.fromLabel(p.getString("mode", "單點") ?: "單點"),
            randomOffset = p.getBoolean("randOff", false),
            randomInterval = p.getBoolean("randInt", false),
            startDelayMs = p.getLong("startDelay", 0),
            sequenceLoops = p.getInt("loops", 1),
            showMarkers = p.getBoolean("showMarkers", true),
            lockMarkersWhileRunning = p.getBoolean("lockMarkers", true),
            hapticFeedback = p.getBoolean("haptic", true),
            compactOverlay = p.getBoolean("compactOverlay", true),
            activeSequenceName = p.getString("activeSeq", "") ?: "",
            darkTheme = p.getBoolean("dark", true)
        )
        val pts = parsePoints(p.getString("points", null))
        if (pts.isNotEmpty()) _points.value = pts
        val current = p.getString("seq_current", null)
        if (!current.isNullOrBlank()) {
            _sequenceSteps.value = parseSteps(current)
        }
        val saved = p.getString("seq_saved", null)
        if (!saved.isNullOrBlank()) {
            _savedSequences.value = parseSaved(saved)
        }
        _stats.value = RunStats(
            lifetimeActions = p.getLong("stat_actions", 0),
            lifetimeSessions = p.getLong("stat_sessions", 0),
            lastSessionActions = p.getInt("stat_last_actions", 0),
            lastSessionMs = p.getLong("stat_last_ms", 0)
        )
    }

    fun updateConfig(transform: (EngineConfig) -> EngineConfig) {
        _config.update(transform)
        persistConfig()
    }

    fun applyPreset(preset: ClickPreset) {
        updateConfig {
            it.copy(
                intervalMs = preset.intervalMs,
                durationMs = preset.durationMs,
                count = preset.count
            )
        }
    }

    fun setPoints(list: List<ClickPoint>) {
        _points.value = if (list.isEmpty()) listOf(ClickPoint(540f, 960f)) else list
        persistPoints()
    }

    fun setPointsLive(list: List<ClickPoint>, persist: Boolean) {
        _points.value = if (list.isEmpty()) listOf(ClickPoint(540f, 960f)) else list
        if (persist) persistPoints()
    }

    fun addPoint(x: Float, y: Float) {
        setPoints(_points.value + ClickPoint(x, y))
    }

    fun updatePoint(index: Int, x: Float, y: Float, persist: Boolean = true) {
        val list = _points.value.toMutableList()
        if (index !in list.indices) return
        list[index] = ClickPoint(x, y)
        setPointsLive(list, persist)
    }

    fun removeLastPoint() {
        val list = _points.value
        if (list.size <= 1) return
        setPoints(list.dropLast(1))
    }

    fun clearPoints() {
        setPoints(listOf(ClickPoint(540f, 960f)))
    }

    fun beginSequenceBatch() {
        sequenceBatch = true
    }

    fun endSequenceBatch() {
        sequenceBatch = false
        persistSequences()
    }

    fun appendSequenceStep(step: SequenceStep) {
        _sequenceSteps.update { it + step }
        if (!sequenceBatch) persistSequences()
    }

    fun setSequenceSteps(steps: List<SequenceStep>) {
        _sequenceSteps.value = steps
        if (!sequenceBatch) persistSequences()
    }

    fun clearSequenceSteps() {
        _sequenceSteps.value = emptyList()
        if (!sequenceBatch) persistSequences()
    }

    fun removeSequenceStep(index: Int) {
        val list = _sequenceSteps.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        setSequenceSteps(list)
    }

    fun moveSequenceStep(from: Int, to: Int) {
        val list = _sequenceSteps.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        if (from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        setSequenceSteps(list)
    }

    fun duplicateSequenceStep(index: Int) {
        val list = _sequenceSteps.value.toMutableList()
        if (index !in list.indices) return
        list.add(index + 1, list[index])
        setSequenceSteps(list)
    }

    fun updateSequenceStep(index: Int, step: SequenceStep) {
        val list = _sequenceSteps.value.toMutableList()
        if (index !in list.indices) return
        list[index] = step
        setSequenceSteps(list)
    }

    fun saveSequenceAs(name: String) {
        if (name.isBlank() || _sequenceSteps.value.isEmpty()) return
        _savedSequences.update { it + (name to _sequenceSteps.value.toList()) }
        updateConfig { it.copy(activeSequenceName = name) }
        persistSequences()
    }

    fun loadSequence(name: String) {
        _savedSequences.value[name]?.let {
            setSequenceSteps(it)
            updateConfig { c -> c.copy(activeSequenceName = name, mode = ClickMode.SEQUENCE) }
        }
    }

    /** Load sequence and switch to SEQUENCE mode without starting. */
    fun switchToSavedSequence(name: String): Boolean {
        val steps = _savedSequences.value[name] ?: return false
        setSequenceSteps(steps)
        updateConfig { c -> c.copy(activeSequenceName = name, mode = ClickMode.SEQUENCE) }
        return true
    }

    fun deleteSavedSequence(name: String) {
        _savedSequences.update { it - name }
        if (_config.value.activeSequenceName == name) {
            updateConfig { it.copy(activeSequenceName = "") }
        }
        persistSequences()
    }

    fun persistSequences() {
        val p = prefs ?: return
        p.edit()
            .putString("seq_current", encodeSteps(_sequenceSteps.value))
            .putString("seq_saved", encodeSaved(_savedSequences.value))
            .apply()
    }

    fun persistPointsNow() {
        persistPoints()
    }

    // ── Stats ──────────────────────────────────────────────────

    fun onSessionStart() {
        _stats.update {
            it.copy(sessionActions = 0, sessionStartedAt = System.currentTimeMillis())
        }
    }

    fun onActionCounted() {
        _stats.update {
            it.copy(
                sessionActions = it.sessionActions + 1,
                lifetimeActions = it.lifetimeActions + 1
            )
        }
        // Debounced persist every ~20 actions to cut IO
        if (_stats.value.lifetimeActions % 20L == 0L) persistStats()
    }

    fun onSessionEnd() {
        val s = _stats.value
        if (s.sessionStartedAt <= 0) return
        val ms = (System.currentTimeMillis() - s.sessionStartedAt).coerceAtLeast(0)
        _stats.value = s.copy(
            lifetimeSessions = s.lifetimeSessions + 1,
            lastSessionActions = s.sessionActions,
            lastSessionMs = ms,
            sessionStartedAt = 0
        )
        persistStats()
    }

    fun resetStats() {
        _stats.value = RunStats()
        persistStats()
    }

    private fun persistStats() {
        val s = _stats.value
        prefs?.edit()
            ?.putLong("stat_actions", s.lifetimeActions)
            ?.putLong("stat_sessions", s.lifetimeSessions)
            ?.putInt("stat_last_actions", s.lastSessionActions)
            ?.putLong("stat_last_ms", s.lastSessionMs)
            ?.apply()
    }

    // ── Import / Export ────────────────────────────────────────

    fun exportBundle(): String {
        val c = _config.value
        val root = JSONObject()
        root.put("magic", EXPORT_MAGIC)
        root.put("ver", EXPORT_VER)
        root.put("interval", c.intervalMs)
        root.put("duration", c.durationMs)
        root.put("count", c.count)
        root.put("mode", c.mode.name)
        root.put("randOff", c.randomOffset)
        root.put("randInt", c.randomInterval)
        root.put("startDelay", c.startDelayMs)
        root.put("loops", c.sequenceLoops)
        root.put("showMarkers", c.showMarkers)
        root.put("lockMarkers", c.lockMarkersWhileRunning)
        root.put("haptic", c.hapticFeedback)
        root.put("compactOverlay", c.compactOverlay)
        root.put("activeSeq", c.activeSequenceName)
        root.put("dark", c.darkTheme)
        root.put("points", encodePointsLine(_points.value))
        root.put("seq_current", encodeSteps(_sequenceSteps.value))
        val saved = JSONObject()
        _savedSequences.value.forEach { (name, steps) ->
            saved.put(name, encodeSteps(steps))
        }
        root.put("seq_saved", saved)
        return root.toString(2)
    }

    /**
     * @return null on success, or error message.
     */
    fun importBundle(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return "內容為空"
        return try {
            if (text.startsWith("{")) importJson(text) else importLegacyText(text)
            null
        } catch (e: Exception) {
            e.message ?: "匯入失敗"
        }
    }

    private fun importJson(text: String): Unit {
        val root = JSONObject(text)
        val magic = root.optString("magic")
        if (magic.isNotEmpty() && magic != EXPORT_MAGIC) {
            throw IllegalArgumentException("不是 AutoClick 匯出檔")
        }
        updateConfig {
            it.copy(
                intervalMs = root.optLong("interval", it.intervalMs),
                durationMs = root.optLong("duration", it.durationMs),
                count = root.optInt("count", it.count),
                mode = ClickMode.fromLabel(root.optString("mode", it.mode.name)),
                randomOffset = root.optBoolean("randOff", it.randomOffset),
                randomInterval = root.optBoolean("randInt", it.randomInterval),
                startDelayMs = root.optLong("startDelay", it.startDelayMs),
                sequenceLoops = root.optInt("loops", it.sequenceLoops),
                showMarkers = root.optBoolean("showMarkers", it.showMarkers),
                lockMarkersWhileRunning = root.optBoolean("lockMarkers", it.lockMarkersWhileRunning),
                hapticFeedback = root.optBoolean("haptic", it.hapticFeedback),
                compactOverlay = root.optBoolean("compactOverlay", it.compactOverlay),
                activeSequenceName = root.optString("activeSeq", it.activeSequenceName),
                darkTheme = root.optBoolean("dark", it.darkTheme)
            )
        }
        val pts = parsePoints(root.optString("points", ""))
        if (pts.isNotEmpty()) setPoints(pts)
        val current = root.optString("seq_current", "")
        if (current.isNotBlank()) setSequenceSteps(parseSteps(current))
        val savedObj = root.optJSONObject("seq_saved")
        if (savedObj != null) {
            val map = linkedMapOf<String, List<SequenceStep>>()
            val keys = savedObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val steps = parseSteps(savedObj.optString(k, ""))
                if (steps.isNotEmpty()) map[k] = steps
            }
            if (map.isNotEmpty()) {
                _savedSequences.value = map
                persistSequences()
            }
        }
    }

    /** Fallback: paste of encodeSteps / seq_saved blocks. */
    private fun importLegacyText(text: String) {
        if (text.contains("\n====\n") || text.lines().any { it.startsWith("click ") || it.startsWith("wait ") }) {
            if (text.contains("\n====\n")) {
                val map = parseSaved(text)
                if (map.isNotEmpty()) {
                    _savedSequences.update { it + map }
                    persistSequences()
                    return
                }
            }
            val steps = parseSteps(text)
            if (steps.isNotEmpty()) {
                setSequenceSteps(steps)
                return
            }
        }
        throw IllegalArgumentException("無法辨識內容（請貼上匯出的 JSON）")
    }

    private fun persistConfig() {
        val c = _config.value
        prefs?.edit()
            ?.putLong("interval", c.intervalMs)
            ?.putLong("duration", c.durationMs)
            ?.putInt("count", c.count)
            ?.putString("mode", c.mode.label())
            ?.putBoolean("randOff", c.randomOffset)
            ?.putBoolean("randInt", c.randomInterval)
            ?.putLong("startDelay", c.startDelayMs)
            ?.putInt("loops", c.sequenceLoops)
            ?.putBoolean("showMarkers", c.showMarkers)
            ?.putBoolean("lockMarkers", c.lockMarkersWhileRunning)
            ?.putBoolean("haptic", c.hapticFeedback)
            ?.putBoolean("compactOverlay", c.compactOverlay)
            ?.putString("activeSeq", c.activeSequenceName)
            ?.putBoolean("dark", c.darkTheme)
            ?.apply()
    }

    private fun persistPoints() {
        prefs?.edit()?.putString("points", encodePointsLine(_points.value))?.apply()
    }

    private fun encodePointsLine(list: List<ClickPoint>): String =
        list.joinToString(";") { "${it.x},${it.y}" }

    private fun parsePoints(raw: String?): List<ClickPoint> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val xy = part.split(",")
            if (xy.size < 2) return@mapNotNull null
            val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
            val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
            ClickPoint(x, y)
        }
    }

    private fun fmt(f: Float): String =
        if (f == f.toInt().toFloat()) f.toInt().toString() else "%.1f".format(f)

    private fun encodeSteps(steps: List<SequenceStep>): String =
        steps.joinToString("\n") { s ->
            when (s.type) {
                SequenceStep.StepType.CLICK ->
                    "click ${fmt(s.x)} ${fmt(s.y)} ${s.delayMs}"
                SequenceStep.StepType.WAIT ->
                    "wait ${s.delayMs}"
                SequenceStep.StepType.SWIPE ->
                    "swipe ${fmt(s.x)} ${fmt(s.y)} ${fmt(s.x2)} ${fmt(s.y2)} ${s.delayMs}"
            }
        }

    private fun parseSteps(text: String): List<SequenceStep> =
        text.lines().mapNotNull { parseStepLine(it) }

    private fun parseStepLine(line: String): SequenceStep? {
        val p = line.trim().split(" ")
        return when (p.getOrNull(0)) {
            "click" -> {
                val x = p.getOrNull(1)?.toFloatOrNull() ?: return null
                val y = p.getOrNull(2)?.toFloatOrNull() ?: return null
                val d = p.getOrNull(3)?.toLongOrNull() ?: 200
                SequenceStep(SequenceStep.StepType.CLICK, x, y, delayMs = d)
            }
            "wait" -> {
                val d = p.getOrNull(1)?.toLongOrNull() ?: return null
                SequenceStep(SequenceStep.StepType.WAIT, delayMs = d)
            }
            "swipe" -> {
                val x = p.getOrNull(1)?.toFloatOrNull() ?: return null
                val y = p.getOrNull(2)?.toFloatOrNull() ?: return null
                val x2 = p.getOrNull(3)?.toFloatOrNull() ?: return null
                val y2 = p.getOrNull(4)?.toFloatOrNull() ?: return null
                val d = p.getOrNull(5)?.toLongOrNull() ?: 300
                SequenceStep(SequenceStep.StepType.SWIPE, x, y, x2, y2, d)
            }
            else -> null
        }
    }

    private fun encodeSaved(map: Map<String, List<SequenceStep>>): String =
        map.entries.joinToString("\n====\n") { (name, steps) ->
            buildString {
                appendLine(name)
                append(encodeSteps(steps))
            }.trimEnd()
        }

    private fun parseSaved(encoded: String): Map<String, List<SequenceStep>> {
        val out = linkedMapOf<String, List<SequenceStep>>()
        encoded.split("\n====\n").forEach { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return@forEach
            val name = lines.first()
            val list = lines.drop(1).mapNotNull { parseStepLine(it) }
            if (list.isNotEmpty()) out[name] = list
        }
        return out
    }
}
