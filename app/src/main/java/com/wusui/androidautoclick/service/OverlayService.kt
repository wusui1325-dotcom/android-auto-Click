package com.wusui.androidautoclick.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.wusui.androidautoclick.data.AppStore
import com.wusui.androidautoclick.engine.ClickEngine
import com.wusui.androidautoclick.engine.NodeKind
import com.wusui.androidautoclick.engine.SessionType
import com.wusui.androidautoclick.engine.TouchNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating control bar (center-drag, H/V) + ordered touch markers (alpha 0.75).
 * Tap marker = position only / delete; long-press = adjust interval & click count.
 */
class OverlayService : Service() {

    companion object {
        @Volatile var isRunning = false
            private set
        const val ACTION_SHOW = "show"
        const val ACTION_HIDE = "hide"
        const val EXTRA_TYPE = "session_type"
        private const val MARKER_ALPHA = 0.75f
    }

    private lateinit var windowManager: WindowManager
    private var barView: View? = null
    private var barParams: WindowManager.LayoutParams? = null
    /** Visual center of the control bar (screen coords). */
    private var barCenterX = 0f
    private var barCenterY = 0f
    private var barCenterInited = false
    private var expanded = true
    private val markers = mutableMapOf<String, Pair<View, WindowManager.LayoutParams>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var layoutToScreenOffsetY = 0
    private var offsetCalibrated = false
    private var draggingMarker = false
    private var markersDirty = true
    private var popupView: View? = null
    private var scope: CoroutineScope? = null
    private var observeJob: Job? = null

    private val pulse = object : Runnable {
        override fun run() {
            updatePlayGlyph()
            if (markersDirty && !draggingMarker) {
                markersDirty = false
                refreshMarkers()
            }
            mainHandler.postDelayed(this, 400)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        startFg()
        observeJob = scope?.launch {
            combine(
                AppStore.nodes,
                AppStore.deleteMode,
                AppStore.barVertical,
                ClickEngine.status
            ) { _, _, _, _ -> }
                .collect { markersDirty = true; rebuildBarIfNeeded() }
        }
    }

    private fun startFg() {
        ServiceCompat.startForeground(
            this,
            ClickNotification.OVERLAY_NOTIFY_ID,
            ClickNotification.buildOverlayKeepAlive(this, "AUTO-C 控制列"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> closeAll()
            else -> {
                startFg()
                val typeName = intent?.getStringExtra(EXTRA_TYPE)
                if (typeName != null && !AppStore.sessionOpen.value) {
                    val type = try {
                        SessionType.valueOf(typeName)
                    } catch (_: Exception) {
                        SessionType.SINGLE
                    }
                    AppStore.beginSession(type)
                }
                if (!AppStore.sessionOpen.value) {
                    AppStore.beginSession(SessionType.SINGLE)
                }
                showBar()
                markersDirty = true
                mainHandler.removeCallbacks(pulse)
                mainHandler.post(pulse)
            }
        }
        return START_STICKY
    }

    private fun closeAll() {
        ClickEngine.stop()
        ClickEngine.stopRecording()
        AppStore.endSession()
        dismissPopup()
        removeBar()
        clearMarkers()
        mainHandler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun baseLp(w: Int, h: Int, focusable: Boolean = false): WindowManager.LayoutParams {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ── Control bar ────────────────────────────────────────────

    private fun showBar() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            closeAll(); return
        }
        rebuildBar()
    }

    private var lastBarKey = ""
    private fun rebuildBarIfNeeded() {
        val st = ClickEngine.status.value
        val key =
            "${expanded}|${AppStore.sessionType.value}|${st.running}|${st.recording}|${AppStore.deleteMode.value}|${AppStore.barVertical.value}"
        if (key != lastBarKey) rebuildBar()
    }

    private fun applyBarCenter(view: View, params: WindowManager.LayoutParams) {
        val w = if (view.width > 0) view.width else view.measuredWidth
        val h = if (view.height > 0) view.height else view.measuredHeight
        if (w <= 0 || h <= 0) return
        params.x = (barCenterX - w / 2f).toInt()
        params.y = (barCenterY - h / 2f).toInt()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    private fun rebuildBar() {
        val st = ClickEngine.status.value
        val vertical = AppStore.barVertical.value
        lastBarKey =
            "${expanded}|${AppStore.sessionType.value}|${st.running}|${st.recording}|${AppStore.deleteMode.value}|$vertical"

        val (sw, sh) = screenSize()
        if (!barCenterInited) {
            barCenterX = dp(12) + dp(120).toFloat()
            barCenterY = dp(140) + dp(28).toFloat()
            barCenterInited = true
        }

        barView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }

        val row = LinearLayout(this).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F2F2F7"))
                setStroke(dp(1), Color.parseColor("#D1D1D6"))
            }
            alpha = 0.96f
        }

        fun iconBtn(symbol: String, label: String, danger: Boolean = false, onClick: () -> Unit) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { onClick() }
            }
            col.addView(TextView(this).apply {
                text = symbol
                gravity = Gravity.CENTER
                setTextColor(if (danger) Color.parseColor("#C4A090") else Color.parseColor("#1D1D1F"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
            })
            col.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#6E6E73"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            })
            row.addView(col)
        }

        val type = AppStore.sessionType.value
        iconBtn(if (st.running) "■" else "▶", if (st.running) "停止" else "開始") {
            ClickEngine.toggle(fromOverlay = true)
            rebuildBar()
        }

        if (expanded) {
            when (type) {
                SessionType.MULTI -> {
                    iconBtn("＋", "加點") {
                        AppStore.addClickPoint(sw / 2f, sh / 2f)
                        markersDirty = true
                    }
                    iconBtn("⇄", "拖拽") {
                        AppStore.addDragGroup(sw * 0.35f, sh / 2f, sw * 0.65f, sh / 2f)
                        markersDirty = true
                    }
                    iconBtn(
                        if (AppStore.deleteMode.value) "⌫!" else "⌫",
                        if (AppStore.deleteMode.value) "選刪" else "刪除",
                        danger = AppStore.deleteMode.value
                    ) {
                        AppStore.setDeleteMode(!AppStore.deleteMode.value)
                        toast(if (AppStore.deleteMode.value) "點一個觸控點／拖拽組以刪除" else "已取消刪除")
                        rebuildBar()
                    }
                }
                SessionType.RECORD -> {
                    iconBtn(if (st.recording) "⏺" else "●", if (st.recording) "停錄" else "錄製") {
                        ClickEngine.toggleRecording()
                        rebuildBar()
                        markersDirty = true
                    }
                    iconBtn("＋", "加點") {
                        AppStore.addClickPoint(sw / 2f, sh / 2f)
                        markersDirty = true
                    }
                }
                SessionType.SINGLE -> { }
            }
            iconBtn("💾", "保存") { showSaveScheme() }
            iconBtn("📂", "方案") { showUseScheme() }
            iconBtn("⚙", "參數") { showParamsPopup() }
            iconBtn("▴", "收起") {
                expanded = false
                rebuildBar()
            }
        } else {
            iconBtn("▾", "展開") {
                expanded = true
                rebuildBar()
            }
        }

        iconBtn("✕", "關閉", danger = true) { closeAll() }

        // Expanded: scroll container so many buttons fit; collapsed: bare row (still draggable)
        val host: View = when {
            expanded && vertical -> ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                addView(row)
            }
            expanded && !vertical -> HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }
            else -> row
        }

        val lp = baseLp(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            x = (barCenterX - dp(60)).toInt()
            y = (barCenterY - dp(24)).toInt()
        }
        setupBarDrag(host, lp)
        try {
            windowManager.addView(host, lp)
            barView = host
            barParams = lp
            host.post { applyBarCenter(host, lp) }
        } catch (_: Exception) {
        }
    }

    private fun updatePlayGlyph() {
        rebuildBarIfNeeded()
    }

    private fun removeBar() {
        barView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        barView = null
    }

    /** Drag by bar center; works expanded and collapsed. */
    private fun setupBarDrag(view: View, params: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var cx0 = 0f
        var cy0 = 0f
        var tx = 0f
        var ty = 0f
        var moved = false
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cx0 = barCenterX
                    cy0 = barCenterY
                    tx = e.rawX
                    ty = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        barCenterX = cx0 + dx
                        barCenterY = cy0 + dy
                        applyBarCenter(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) dispatchChildClick(v, e.rawX, e.rawY)
                    true
                }
                else -> false
            }
        }
    }

    private fun dispatchChildClick(root: View, rawX: Float, rawY: Float): Boolean {
        if (root !is android.view.ViewGroup) return false
        val loc = IntArray(2)
        for (i in 0 until root.childCount) {
            val c = root.getChildAt(i)
            if (!c.isShown) continue
            c.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX < loc[0] + c.width &&
                rawY >= loc[1] && rawY < loc[1] + c.height
            ) {
                if (c.hasOnClickListeners()) {
                    c.performClick(); return true
                }
                if (c is android.view.ViewGroup && dispatchChildClick(c, rawX, rawY)) return true
            }
        }
        return false
    }

    // ── Markers ────────────────────────────────────────────────

    private fun clearMarkers() {
        markers.values.forEach { try { windowManager.removeView(it.first) } catch (_: Exception) {} }
        markers.clear()
    }

    private fun refreshMarkers() {
        clearMarkers()
        AppStore.orderedNodes().forEach { addMarker(it) }
    }

    private fun addMarker(node: TouchNode) {
        val size = dp(44)
        val locked = ClickEngine.status.value.running && !AppStore.deleteMode.value

        val wrap = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), Color.WHITE)
            }
            alpha = MARKER_ALPHA
            addView(TextView(this@OverlayService).apply {
                layoutParams = FrameLayout.LayoutParams(size - dp(6), size - dp(6)).apply {
                    gravity = Gravity.CENTER
                }
                gravity = Gravity.CENTER
                text = when (node.kind) {
                    NodeKind.DRAG_START -> "↗${node.order}"
                    NodeKind.DRAG_END -> "↙${node.order}"
                    else -> "${node.order}"
                }
                setTextColor(Color.parseColor("#FF3B30"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), Color.BLACK)
                }
            })
        }

        val params = baseLp(size, size).apply {
            x = (node.x - size / 2f).toInt()
            y = (node.y - size / 2f - layoutToScreenOffsetY).toInt()
            if (locked) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (!locked) {
            val longMs = ViewConfiguration.getLongPressTimeout().toLong()
            var ix = 0
            var iy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            var longFired = false
            val longRun = Runnable {
                longFired = true
                showNodePopup(node)
            }
            wrap.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        draggingMarker = true
                        ix = params.x; iy = params.y
                        tx = e.rawX; ty = e.rawY
                        moved = false
                        longFired = false
                        mainHandler.postDelayed(longRun, longMs)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 4 || abs(dy) > 4) {
                            if (!moved) mainHandler.removeCallbacks(longRun)
                            moved = true
                        }
                        params.x = ix + dx
                        params.y = iy + dy
                        try { windowManager.updateViewLayout(wrap, params) } catch (_: Exception) {}
                        AppStore.moveNode(
                            node.id,
                            params.x + size / 2f,
                            params.y + size / 2f + layoutToScreenOffsetY
                        )
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        draggingMarker = false
                        mainHandler.removeCallbacks(longRun)
                        AppStore.moveNode(
                            node.id,
                            params.x + size / 2f,
                            params.y + size / 2f + layoutToScreenOffsetY
                        )
                        // Tap: delete-mode only. No adjust popup on short tap.
                        if (!moved && !longFired && AppStore.deleteMode.value) {
                            AppStore.removeNodeOrGroup(node.id)
                            markersDirty = true
                            rebuildBar()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            windowManager.addView(wrap, params)
            markers[node.id] = wrap to params
            if (!offsetCalibrated) {
                wrap.post {
                    try {
                        val loc = IntArray(2)
                        wrap.getLocationOnScreen(loc)
                        layoutToScreenOffsetY = loc[1] - params.y
                        offsetCalibrated = true
                        markersDirty = true
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // ── Popups ─────────────────────────────────────────────────

    private fun dismissPopup() {
        popupView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        popupView = null
    }

    private fun showPanel(title: String, build: LinearLayout.() -> Unit) {
        dismissPopup()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F2F2F7"))
                setStroke(dp(1), Color.parseColor("#D1D1D6"))
            }
        }
        card.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#1D1D1F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        })
        card.build()
        card.addView(TextView(this).apply {
            text = "關閉"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6E6E73"))
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { dismissPopup() }
        })
        val (sw, sh) = screenSize()
        val lp = baseLp((sw * 0.82f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, focusable = true).apply {
            x = (sw * 0.09f).toInt()
            y = (sh * 0.22f).toInt()
        }
        try {
            windowManager.addView(card, lp)
            popupView = card
        } catch (_: Exception) {
            toast("無法顯示浮窗：$title")
        }
    }

    private fun fieldRow(label: String, value: String): Pair<LinearLayout, EditText> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#1D1D1F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val et = EditText(this).apply {
            setText(value)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#1D1D1F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(Color.parseColor("#E5E5EA"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            minWidth = dp(88)
        }
        row.addView(et)
        return row to et
    }

    private fun showNodePopup(node: TouchNode) {
        val live = AppStore.nodes.value.find { it.id == node.id } ?: node
        val ordered = AppStore.orderedNodes()
        val isLast = ordered.lastOrNull()?.id == live.id
        val intervalLabel = if (isLast) {
            "與下次間隔(ms)＝下一輪循環等待"
        } else {
            "與下一序間隔(ms)"
        }
        showPanel("觸控點 ${live.order}（長按調整）") {
            val (r1, etInterval) = fieldRow(intervalLabel, live.intervalToNextMs.toString())
            addView(r1)
            val (r2, etClicks) = fieldRow("單次點擊次數", live.clickCount.toString())
            if (live.kind == NodeKind.CLICK) addView(r2)
            addView(TextView(this@OverlayService).apply {
                text = "套用"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#1D1D1F"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(12), 0, 0)
                setOnClickListener {
                    val interval = etInterval.text.toString().toLongOrNull()?.coerceAtLeast(0)
                        ?: live.intervalToNextMs
                    val clicks = etClicks.text.toString().toIntOrNull()?.coerceAtLeast(1)
                        ?: live.clickCount
                    AppStore.updateNode(live.id) {
                        it.copy(intervalToNextMs = interval, clickCount = clicks)
                    }
                    dismissPopup()
                    toast("已更新點 ${live.order}")
                }
            })
        }
    }

    private fun showParamsPopup() {
        val p = AppStore.params.value
        showPanel("參數調整") {
            val (r1, etCycles) = fieldRow(
                "循環次數(空=無限)",
                if (p.cycleCount < 0) "" else p.cycleCount.toString()
            )
            addView(r1)
            val (r2, etStop) = fieldRow("多久後停止(ms,0=無)", p.stopAfterMs.toString())
            addView(r2)
            addView(TextView(this@OverlayService).apply {
                text = "循環間隔＝最後一點「與下次間隔」（無獨立循環間隙）"
                setTextColor(Color.parseColor("#6E6E73"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, dp(8))
            })
            var jitter = p.jitter
            val jitterTv = TextView(this@OverlayService).apply {
                text = if (jitter) "點擊抖動：開（防偵測）" else "點擊抖動：關"
                setTextColor(Color.parseColor("#1D1D1F"))
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    jitter = !jitter
                    text = if (jitter) "點擊抖動：開（防偵測）" else "點擊抖動：關"
                }
            }
            addView(jitterTv)
            addView(TextView(this@OverlayService).apply {
                text = "套用"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#1D1D1F"))
                typeface = Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, dp(8), 0, 0)
                setOnClickListener {
                    val cycles = etCycles.text.toString().let {
                        if (it.isBlank()) -1 else it.toIntOrNull() ?: p.cycleCount
                    }
                    val stop = etStop.text.toString().toLongOrNull()?.coerceAtLeast(0) ?: p.stopAfterMs
                    AppStore.setParams(p.copy(cycleCount = cycles, stopAfterMs = stop, jitter = jitter))
                    dismissPopup()
                    toast("參數已套用")
                }
            })
        }
    }

    private fun showSaveScheme() {
        showPanel("保存方案") {
            val et = EditText(this@OverlayService).apply {
                hint = "方案名稱"
                setText(AppStore.sessionType.value.label())
                setTextColor(Color.parseColor("#1D1D1F"))
                setHintTextColor(Color.parseColor("#8E8E93"))
                setBackgroundColor(Color.parseColor("#E5E5EA"))
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            addView(et)
            addView(TextView(this@OverlayService).apply {
                text = "保存"
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1D1D1F"))
                setPadding(0, dp(12), 0, 0)
                setOnClickListener {
                    val name = et.text.toString().trim()
                    if (AppStore.saveScheme(name)) {
                        dismissPopup(); toast("已保存「$name」")
                    } else toast("保存失敗（需有觸控點且名稱非空）")
                }
            })
        }
    }

    private fun showUseScheme() {
        val names = AppStore.schemes.value.keys.sorted()
        if (names.isEmpty()) {
            toast("尚無已存方案"); return
        }
        showPanel("使用方案") {
            names.forEach { name ->
                val s = AppStore.schemes.value[name]!!
                addView(TextView(this@OverlayService).apply {
                    text = "$name  ·  ${s.type.label()} · ${s.nodes.size} 點"
                    setTextColor(Color.parseColor("#1D1D1F"))
                    setPadding(0, dp(10), 0, dp(10))
                    setOnClickListener {
                        AppStore.useScheme(name)
                        dismissPopup()
                        markersDirty = true
                        rebuildBar()
                        toast("已載入「$name」")
                    }
                })
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope?.cancel()
        dismissPopup()
        removeBar()
        clearMarkers()
        mainHandler.removeCallbacksAndMessages(null)
        isRunning = false
        super.onDestroy()
    }
}
