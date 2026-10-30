package com.wusui.androidautoclick.data

import android.content.Context
import android.content.SharedPreferences
import com.wusui.androidautoclick.engine.ClickMode
import com.wusui.androidautoclick.engine.ClickPoint
import com.wusui.androidautoclick.engine.EngineConfig
import com.wusui.androidautoclick.engine.SequenceStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for all persisted settings, points, and sequences.
 */
object SettingsRepository {

    private const val PREF = "autoclick_v2"
    private var prefs: SharedPreferences? = null
    var appContext: Context? = null
        private set

    private val _config = MutableStateFlow(EngineConfig())
    val config: StateFlow<EngineConfig> = _config.asStateFlow()

    private val _points = MutableStateFlow(listOf(ClickPoint(540f, 960f)))
    val points: StateFlow<List<ClickPoint>> = _points.asStateFlow()

    private val _sequenceSteps = MutableStateFlow<List<SequenceStep>>(emptyList())
    val sequenceSteps: StateFlow<List<SequenceStep>> = _sequenceSteps.asStateFlow()

    private val _savedSequences = MutableStateFlow<Map<String, List<SequenceStep>>>(emptyMap())
    val savedSequences: StateFlow<Map<String, List<SequenceStep>>> = _savedSequences.asStateFlow()

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
    }

    fun updateConfig(transform: (EngineConfig) -> EngineConfig) {
        _config.update(transform)
        persistConfig()
    }

    fun setPoints(list: List<ClickPoint>) {
        _points.value = if (list.isEmpty()) listOf(ClickPoint(540f, 960f)) else list
        persistPoints()
    }

    fun addPoint(x: Float, y: Float) {
        setPoints(_points.value + ClickPoint(x, y))
    }

    fun updatePoint(index: Int, x: Float, y: Float) {
        val list = _points.value.toMutableList()
        if (index !in list.indices) return
        list[index] = ClickPoint(x, y)
        setPoints(list)
    }

    fun removeLastPoint() {
        val list = _points.value
        if (list.size <= 1) return
        setPoints(list.dropLast(1))
    }

    fun clearPoints() {
        setPoints(listOf(ClickPoint(540f, 960f)))
    }

    fun appendSequenceStep(step: SequenceStep) {
        _sequenceSteps.update { it + step }
        persistSequences()
    }

    fun setSequenceSteps(steps: List<SequenceStep>) {
        _sequenceSteps.value = steps
        persistSequences()
    }

    fun clearSequenceSteps() {
        _sequenceSteps.value = emptyList()
        persistSequences()
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
        val item = list.removeAt(from)
        list.add(to, item)
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
        persistSequences()
    }

    fun loadSequence(name: String) {
        _savedSequences.value[name]?.let { setSequenceSteps(it) }
    }

    fun deleteSavedSequence(name: String) {
        _savedSequences.update { it - name }
        persistSequences()
    }

    fun persistSequences() {
        val p = prefs ?: return
        p.edit()
            .putString("seq_current", encodeSteps(_sequenceSteps.value))
            .putString("seq_saved", encodeSaved(_savedSequences.value))
            .apply()
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
            ?.putBoolean("dark", c.darkTheme)
            ?.apply()
    }

    private fun persistPoints() {
        val encoded = _points.value.joinToString(";") { "${it.x},${it.y}" }
        prefs?.edit()?.putString("points", encoded)?.apply()
    }

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

    private fun encodeSteps(steps: List<SequenceStep>): String =
        steps.joinToString("\n") { s ->
            when (s.type) {
                SequenceStep.StepType.CLICK ->
                    "click ${s.x.toInt()} ${s.y.toInt()} ${s.delayMs}"
                SequenceStep.StepType.WAIT ->
                    "wait ${s.delayMs}"
                SequenceStep.StepType.SWIPE ->
                    "swipe ${s.x.toInt()} ${s.y.toInt()} ${s.x2.toInt()} ${s.y2.toInt()} ${s.delayMs}"
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
