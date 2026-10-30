package com.wusui.androidautoclick.data

import android.content.Context
import android.content.SharedPreferences
import com.wusui.androidautoclick.engine.GlobalParams
import com.wusui.androidautoclick.engine.NodeKind
import com.wusui.androidautoclick.engine.Scheme
import com.wusui.androidautoclick.engine.SessionType
import com.wusui.androidautoclick.engine.TouchNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Live session + saved schemes. Replaces the old SettingsRepository pile.
 */
object AppStore {

    private const val PREF = "autoclick_v3"
    private var prefs: SharedPreferences? = null
    var appContext: Context? = null
        private set

    private val _type = MutableStateFlow(SessionType.SINGLE)
    val sessionType: StateFlow<SessionType> = _type.asStateFlow()

    private val _nodes = MutableStateFlow<List<TouchNode>>(emptyList())
    val nodes: StateFlow<List<TouchNode>> = _nodes.asStateFlow()

    private val _params = MutableStateFlow(GlobalParams())
    val params: StateFlow<GlobalParams> = _params.asStateFlow()

    private val _schemes = MutableStateFlow<Map<String, Scheme>>(emptyMap())
    val schemes: StateFlow<Map<String, Scheme>> = _schemes.asStateFlow()

    private val _sessionOpen = MutableStateFlow(false)
    val sessionOpen: StateFlow<Boolean> = _sessionOpen.asStateFlow()

    private val _deleteMode = MutableStateFlow(false)
    val deleteMode: StateFlow<Boolean> = _deleteMode.asStateFlow()

    /** true = vertical control bar */
    private val _barVertical = MutableStateFlow(false)
    val barVertical: StateFlow<Boolean> = _barVertical.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        loadSchemes()
        loadParams()
        _barVertical.value = prefs?.getBoolean("barVertical", false) ?: false
    }

    fun setBarVertical(vertical: Boolean) {
        _barVertical.value = vertical
        prefs?.edit()?.putBoolean("barVertical", vertical)?.apply()
    }

    fun beginSession(type: SessionType) {
        _type.value = type
        _deleteMode.value = false
        _sessionOpen.value = true
        when (type) {
            SessionType.SINGLE -> {
                val (cx, cy) = defaultCenter()
                _nodes.value = listOf(
                    TouchNode(order = 1, x = cx, y = cy, intervalToNextMs = 200, clickCount = 1)
                )
            }
            SessionType.MULTI -> {
                val (cx, cy) = defaultCenter()
                _nodes.value = listOf(
                    TouchNode(order = 1, x = cx, y = cy, intervalToNextMs = 200, clickCount = 1)
                )
            }
            SessionType.RECORD -> {
                _nodes.value = emptyList()
            }
        }
    }

    fun endSession() {
        _sessionOpen.value = false
        _deleteMode.value = false
        _nodes.value = emptyList()
    }

    fun setParams(p: GlobalParams) {
        _params.value = p
        persistParams()
    }

    fun updateParams(transform: (GlobalParams) -> GlobalParams) {
        setParams(transform(_params.value))
    }

    fun setDeleteMode(on: Boolean) {
        _deleteMode.value = on
    }

    fun addClickPoint(x: Float, y: Float): TouchNode {
        val next = (_nodes.value.maxOfOrNull { it.order } ?: 0) + 1
        val node = TouchNode(order = next, x = x, y = y)
        _nodes.update { it + node }
        renumber()
        return node
    }

    fun addDragGroup(x1: Float, y1: Float, x2: Float, y2: Float) {
        val gid = UUID.randomUUID().toString()
        val base = (_nodes.value.maxOfOrNull { it.order } ?: 0)
        val start = TouchNode(
            order = base + 1, x = x1, y = y1,
            kind = NodeKind.DRAG_START, groupId = gid, intervalToNextMs = 300, clickCount = 1
        )
        val end = TouchNode(
            order = base + 2, x = x2, y = y2,
            kind = NodeKind.DRAG_END, groupId = gid, intervalToNextMs = 200, clickCount = 1
        )
        _nodes.update { it + start + end }
        renumber()
    }

    fun updateNode(id: String, transform: (TouchNode) -> TouchNode) {
        _nodes.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun moveNode(id: String, x: Float, y: Float) {
        updateNode(id) { it.copy(x = x, y = y) }
    }

    fun removeNodeOrGroup(id: String) {
        val target = _nodes.value.find { it.id == id } ?: return
        _nodes.update { list ->
            if (target.groupId != null) list.filter { it.groupId != target.groupId }
            else list.filter { it.id != id }
        }
        renumber()
        _deleteMode.value = false
    }

    fun clearNodes() {
        _nodes.value = emptyList()
    }

    fun appendRecordedClick(x: Float, y: Float, gapMs: Long) {
        val next = (_nodes.value.maxOfOrNull { it.order } ?: 0) + 1
        // Apply gap to previous node's interval if present
        if (_nodes.value.isNotEmpty()) {
            val last = _nodes.value.last()
            _nodes.update { list ->
                list.dropLast(1) + last.copy(intervalToNextMs = gapMs.coerceAtLeast(0)) +
                    TouchNode(order = next, x = x, y = y)
            }
        } else {
            _nodes.update { it + TouchNode(order = next, x = x, y = y) }
        }
        renumber()
    }

    private fun renumber() {
        _nodes.update { list ->
            list.sortedBy { it.order }.mapIndexed { i, n -> n.copy(order = i + 1) }
        }
    }

    fun orderedNodes(): List<TouchNode> = _nodes.value.sortedBy { it.order }

    // ── Schemes ────────────────────────────────────────────────

    fun saveScheme(name: String): Boolean {
        if (name.isBlank()) return false
        if (_nodes.value.isEmpty() && _type.value != SessionType.RECORD) return false
        val scheme = Scheme(
            name = name.trim(),
            type = _type.value,
            nodes = _nodes.value.map { it.copy() },
            params = _params.value
        )
        _schemes.update { it + (scheme.name to scheme) }
        persistSchemes()
        return true
    }

    fun useScheme(name: String): Boolean {
        val s = _schemes.value[name] ?: return false
        _type.value = s.type
        _nodes.value = s.nodes.map { it.copy(id = UUID.randomUUID().toString()) }
        _params.value = s.params
        _sessionOpen.value = true
        _deleteMode.value = false
        persistParams()
        return true
    }

    fun deleteScheme(name: String) {
        _schemes.update { it - name }
        persistSchemes()
    }

    fun snapshotScheme(name: String = "目前"): Scheme =
        Scheme(name, _type.value, _nodes.value, _params.value)

    private fun defaultCenter(): Pair<Float, Float> {
        val dm = appContext?.resources?.displayMetrics
        val w = dm?.widthPixels?.toFloat() ?: 540f
        val h = dm?.heightPixels?.toFloat() ?: 960f
        return w / 2f to h / 2f
    }

    private fun loadParams() {
        val p = prefs ?: return
        _params.value = GlobalParams(
            cycleCount = p.getInt("cycleCount", -1),
            stopAfterMs = p.getLong("stopAfter", 0),
            jitter = p.getBoolean("jitter", false),
            tapDurationMs = p.getLong("tapDur", 50),
            swipeDurationMs = p.getLong("swipeDur", 280)
        )
    }

    private fun persistParams() {
        val c = _params.value
        prefs?.edit()
            ?.putInt("cycleCount", c.cycleCount)
            ?.putLong("stopAfter", c.stopAfterMs)
            ?.putBoolean("jitter", c.jitter)
            ?.putLong("tapDur", c.tapDurationMs)
            ?.putLong("swipeDur", c.swipeDurationMs)
            ?.apply()
    }

    private fun loadSchemes() {
        val raw = prefs?.getString("schemes", null) ?: return
        try {
            val arr = JSONArray(raw)
            val map = linkedMapOf<String, Scheme>()
            for (i in 0 until arr.length()) {
                parseScheme(arr.getJSONObject(i))?.let { map[it.name] = it }
            }
            _schemes.value = map
        } catch (_: Exception) {
        }
    }

    private fun persistSchemes() {
        val arr = JSONArray()
        _schemes.value.values.forEach { arr.put(schemeToJson(it)) }
        prefs?.edit()?.putString("schemes", arr.toString())?.apply()
    }

    private fun schemeToJson(s: Scheme): JSONObject = JSONObject().apply {
        put("name", s.name)
        put("type", s.type.name)
        put("cycleCount", s.params.cycleCount)
        put("stopAfter", s.params.stopAfterMs)
        put("jitter", s.params.jitter)
        put("tapDur", s.params.tapDurationMs)
        put("swipeDur", s.params.swipeDurationMs)
        val nodes = JSONArray()
        s.nodes.forEach { n ->
            nodes.put(JSONObject().apply {
                put("order", n.order)
                put("x", n.x.toDouble())
                put("y", n.y.toDouble())
                put("interval", n.intervalToNextMs)
                put("clicks", n.clickCount)
                put("kind", n.kind.name)
                put("groupId", n.groupId ?: JSONObject.NULL)
            })
        }
        put("nodes", nodes)
    }

    private fun parseScheme(o: JSONObject): Scheme? {
        val name = o.optString("name").ifBlank { return null }
        val type = try {
            SessionType.valueOf(o.optString("type", "MULTI"))
        } catch (_: Exception) {
            SessionType.MULTI
        }
        val params = GlobalParams(
            cycleCount = o.optInt("cycleCount", -1),
            stopAfterMs = o.optLong("stopAfter", 0),
            jitter = o.optBoolean("jitter", false),
            tapDurationMs = o.optLong("tapDur", 50),
            swipeDurationMs = o.optLong("swipeDur", 280)
        )
        val arr = o.optJSONArray("nodes") ?: JSONArray()
        val nodes = mutableListOf<TouchNode>()
        for (i in 0 until arr.length()) {
            val n = arr.getJSONObject(i)
            val gid = if (n.isNull("groupId")) null else n.optString("groupId").ifBlank { null }
            val kind = try {
                NodeKind.valueOf(n.optString("kind", "CLICK"))
            } catch (_: Exception) {
                NodeKind.CLICK
            }
            nodes += TouchNode(
                order = n.optInt("order", i + 1),
                x = n.optDouble("x", 0.0).toFloat(),
                y = n.optDouble("y", 0.0).toFloat(),
                intervalToNextMs = n.optLong("interval", 200),
                clickCount = n.optInt("clicks", 1).coerceAtLeast(1),
                kind = kind,
                groupId = gid
            )
        }
        return Scheme(name, type, nodes, params)
    }
}
