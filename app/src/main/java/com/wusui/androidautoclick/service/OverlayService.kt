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
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
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
import com.wusui.androidautoclick.R
import com.wusui.androidautoclick.data.AppStore
import com.wusui.androidautoclick.i18n.LocaleHelper
import com.wusui.androidautoclick.engine.ClickEngine
import com.wusui.androidautoclick.engine.NodeKind
import com.wusui.androidautoclick.engine.SessionType
import com.wusui.androidautoclick.engine.TouchNode
import com.wusui.androidautoclick.ui.FlatIconView
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
    private var barAnimating = false
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
                AppStore.barSize,
                ClickEngine.status
            ) { _, _, _, _, _ -> }
                .collect { markersDirty = true; rebuildBarIfNeeded() }
        }
        scope?.launch {
            var langReady = false
            AppStore.language.collect {
                if (!langReady) { langReady = true; return@collect }
                ClickNotification.ensureChannel(this@OverlayService)
                startFg()
                lastBarKey = ""
                rebuildBar(animated = true)
                dismissPopup()
            }
        }
    }

    private fun startFg() {
        ServiceCompat.startForeground(
            this,
            ClickNotification.OVERLAY_NOTIFY_ID,
            ClickNotification.buildOverlayKeepAlive(this, s(R.string.notif_overlay_bar)),
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

    // ── i18n ───────────────────────────────────────────────────
    private fun s(id: Int): String = LocaleHelper.str(this, id)
    private fun s(id: Int, vararg args: Any): String = LocaleHelper.str(this, id, *args)

    // ── Control bar ────────────────────────────────────────────

    private fun showBar() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            closeAll(); return
        }
        rebuildBar(animated = barView == null)
    }

    private var lastBarKey = ""
    private fun rebuildBarIfNeeded() {
        val st = ClickEngine.status.value
        val key =
            "${expanded}|${AppStore.sessionType.value}|${st.running}|${st.recording}|${AppStore.deleteMode.value}|${AppStore.barVertical.value}|${AppStore.barSize.value}"
        if (key == lastBarKey) return
        // Animate only structure changes (expand / orientation / size), not play/record glyph flips
        val old = lastBarKey.split("|")
        val neu = key.split("|")
        val structural = old.size >= 7 && neu.size >= 7 &&
            (old[0] != neu[0] || old[5] != neu[5] || old[6] != neu[6])
        rebuildBar(animated = structural && lastBarKey.isNotEmpty())
    }

    /** Last laid-out bar size for center→top-left conversion. */
    private var barW = 0
    private var barH = 0

    private fun applyBarCenter(view: View, params: WindowManager.LayoutParams) {
        val w = when {
            view.width > 0 -> view.width
            view.measuredWidth > 0 -> view.measuredWidth
            barW > 0 -> barW
            else -> dp(120)
        }
        val h = when {
            view.height > 0 -> view.height
            view.measuredHeight > 0 -> view.measuredHeight
            barH > 0 -> barH
            else -> dp(48)
        }
        barW = w
        barH = h
        params.x = (barCenterX - w / 2f).toInt()
        params.y = (barCenterY - h / 2f).toInt()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    private fun rebuildBar(animated: Boolean = false) {
        if (barAnimating && animated) return
        val st = ClickEngine.status.value
        val vertical = AppStore.barVertical.value
        val sizeIdx = AppStore.barSize.value
        lastBarKey =
            "${expanded}|${AppStore.sessionType.value}|${st.running}|${st.recording}|${AppStore.deleteMode.value}|$vertical|$sizeIdx"

        val oldHost = barView
        if (animated && oldHost != null) {
            barAnimating = true
            oldHost.animate().cancel()
            oldHost.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(120L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    try { windowManager.removeView(oldHost) } catch (_: Exception) {}
                    if (barView === oldHost) barView = null
                    mountBar(enterAnim = true)
                }
                .start()
        } else {
            oldHost?.animate()?.cancel()
            oldHost?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            barView = null
            mountBar(enterAnim = animated)
        }
    }

    private fun mountBar(enterAnim: Boolean) {
        val st = ClickEngine.status.value
        val vertical = AppStore.barVertical.value
        val sizeIdx = AppStore.barSize.value
        val scale = when (sizeIdx) { 0 -> 0.82f; 2 -> 1.22f; else -> 1f }
        fun sdp(v: Int) = (dp(v) * scale).toInt().coerceAtLeast(1)

        val (sw, sh) = screenSize()
        if (!barCenterInited) {
            barCenterX = dp(12) + dp(120).toFloat()
            barCenterY = dp(140) + dp(28).toFloat()
            barCenterInited = true
        }

        val row = LinearLayout(this).apply {
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(sdp(6), sdp(6), sdp(6), sdp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F2F2F7"))
                setStroke(dp(1), Color.parseColor("#D1D1D6"))
            }
            alpha = 0.96f
        }

        fun iconBtn(kind: FlatIconView.Kind, label: String, danger: Boolean = false, onClick: () -> Unit) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(sdp(8), sdp(4), sdp(8), sdp(4))
                setOnClickListener { onClick() }
            }
            val iconSize = sdp(22)
            col.addView(FlatIconView(this).apply {
                this.kind = kind
                ink = if (danger) Color.parseColor("#8A8A8E") else Color.parseColor("#1D1D1F")
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            })
            col.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#6E6E73"))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, sdp(9).toFloat().coerceAtLeast(8f))
            })
            row.addView(col)
        }

        val type = AppStore.sessionType.value
        iconBtn(
            if (st.running) FlatIconView.Kind.STOP else FlatIconView.Kind.PLAY,
            if (st.running) s(R.string.action_stop) else s(R.string.action_start)
        ) {
            ClickEngine.toggle(fromOverlay = true)
            rebuildBar(animated = false)
        }

        if (expanded) {
            when (type) {
                SessionType.MULTI -> {
                    iconBtn(FlatIconView.Kind.PLUS, s(R.string.action_add_point)) {
                        AppStore.addClickPoint(sw / 2f, sh / 2f)
                        markersDirty = true
                    }
                    iconBtn(FlatIconView.Kind.DRAG, s(R.string.action_drag)) {
                        AppStore.addDragGroup(sw * 0.35f, sh / 2f, sw * 0.65f, sh / 2f)
                        markersDirty = true
                    }
                    iconBtn(
                        FlatIconView.Kind.DELETE,
                        if (AppStore.deleteMode.value) s(R.string.action_delete_pick) else s(R.string.action_delete),
                        danger = AppStore.deleteMode.value
                    ) {
                        AppStore.setDeleteMode(!AppStore.deleteMode.value)
                        toast(if (AppStore.deleteMode.value) s(R.string.toast_delete_hint) else s(R.string.toast_delete_cancel))
                        rebuildBar(animated = false)
                    }
                }
                SessionType.RECORD -> {
                    iconBtn(
                        if (st.recording) FlatIconView.Kind.RECORD_ON else FlatIconView.Kind.RECORD,
                        if (st.recording) s(R.string.action_record_stop) else s(R.string.action_record)
                    ) {
                        ClickEngine.toggleRecording()
                        rebuildBar(animated = false)
                        markersDirty = true
                    }
                    iconBtn(FlatIconView.Kind.PLUS, s(R.string.action_add_point)) {
                        AppStore.addClickPoint(sw / 2f, sh / 2f)
                        markersDirty = true
                    }
                }
                SessionType.SINGLE -> { }
            }
            iconBtn(FlatIconView.Kind.SAVE, s(R.string.action_save)) { showSaveScheme() }
            iconBtn(FlatIconView.Kind.SCHEME, s(R.string.action_scheme)) { showUseScheme() }
            iconBtn(FlatIconView.Kind.PARAMS, s(R.string.action_params)) { showParamsPopup() }
            iconBtn(FlatIconView.Kind.COLLAPSE, s(R.string.action_collapse)) {
                expanded = false
                rebuildBar(animated = true)
            }
        } else {
            iconBtn(FlatIconView.Kind.EXPAND, s(R.string.action_expand)) {
                expanded = true
                rebuildBar(animated = true)
            }
        }

        iconBtn(FlatIconView.Kind.CLOSE, s(R.string.action_close), danger = true) { closeAll() }

        // Soft stagger: mid buttons start slightly transparent (expand feel)
        if (enterAnim && expanded && row.childCount > 2) {
            for (i in 1 until row.childCount - 1) {
                row.getChildAt(i).alpha = 0f
            }
        }

        val content: View = when {
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

        val host = DragShell(this).apply { addView(content) }

        val lp = baseLp(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            x = (barCenterX - dp(60)).toInt()
            y = (barCenterY - dp(24)).toInt()
        }
        wireBarDrag(host, lp)
        try {
            if (enterAnim) {
                host.alpha = 0f
                host.scaleX = if (vertical) 1f else 0.94f
                host.scaleY = if (vertical) 0.94f else 1f
            }
            windowManager.addView(host, lp)
            barView = host
            barParams = lp
            host.post {
                if (host.width > 0) { barW = host.width; barH = host.height }
                applyBarCenter(host, lp)
                if (enterAnim) {
                    host.animate().cancel()
                    host.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            barAnimating = false
                            // Stagger mid buttons in
                            if (expanded) {
                                val r = row
                                for (i in 1 until r.childCount - 1) {
                                    val c = r.getChildAt(i)
                                    c.animate().cancel()
                                    c.animate()
                                        .alpha(1f)
                                        .setStartDelay((i * 18L).coerceAtMost(90L))
                                        .setDuration(140L)
                                        .setInterpolator(DecelerateInterpolator())
                                        .start()
                                }
                            }
                        }
                        .start()
                } else {
                    barAnimating = false
                }
            }
        } catch (_: Exception) {
            barAnimating = false
        }
    }

    private fun updatePlayGlyph() {
        rebuildBarIfNeeded()
    }

    private fun removeBar() {
        barAnimating = false
        barView?.animate()?.cancel()
        barView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        barView = null
    }

    /**
     * Center-anchored drag via [DragShell] intercept: children keep clicks/scroll until
     * finger moves past touch slop, then shell steals the gesture and moves the bar.
     */
    private fun wireBarDrag(shell: DragShell, params: WindowManager.LayoutParams) {
        shell.onDrag = { dx, dy ->
            barCenterX = shell.downCenterX + dx
            barCenterY = shell.downCenterY + dy
            applyBarCenter(shell, params)
        }
        shell.captureDownCenter = {
            shell.downCenterX = barCenterX
            shell.downCenterY = barCenterY
        }
    }

    /**
     * Intercepts MOVE past slop so clickable icons cannot eat the drag gesture
     * (ViewGroup OnTouchListener is too late — kids receive DOWN first).
     */
    private class DragShell(context: android.content.Context) : FrameLayout(context) {
        var onDrag: ((dx: Float, dy: Float) -> Unit)? = null
        var captureDownCenter: (() -> Unit)? = null
        var downCenterX = 0f
        var downCenterY = 0f
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var dragging = false

        init {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    dragging = false
                    captureDownCenter?.invoke()
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        val dx = ev.rawX - downRawX
                        val dy = ev.rawY - downRawY
                        if (abs(dx) > slop || abs(dy) > slop) {
                            dragging = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                    return dragging
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val was = dragging
                    dragging = false
                    return was
                }
            }
            return false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // No child took it (hit padding) — still allow drag from here
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    dragging = false
                    captureDownCenter?.invoke()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                    }
                    if (dragging) {
                        onDrag?.invoke(dx, dy)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    return true
                }
            }
            return super.onTouchEvent(ev)
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
                    NodeKind.DRAG_START -> "↑${node.order}"
                    NodeKind.DRAG_END -> "↓${node.order}"
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

        // Ensure child TextView cannot steal touches from wrap's listener path
        for (i in 0 until wrap.childCount) {
            wrap.getChildAt(i).apply {
                isClickable = false
                isFocusable = false
            }
        }
        wrap.isClickable = true
        wrap.isFocusable = false

        if (!locked) {
            val longMs = ViewConfiguration.getLongPressTimeout().toLong()
            val slop = ViewConfiguration.get(this).scaledTouchSlop
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
            // Use DragShell-style: handle on the wrap via listener after making it the touch target
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
                        if (abs(dx) > slop || abs(dy) > slop) {
                            if (!moved) mainHandler.removeCallbacks(longRun)
                            moved = true
                            params.x = ix + dx
                            params.y = iy + dy
                            try { windowManager.updateViewLayout(wrap, params) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(longRun)
                        if (moved) {
                            AppStore.moveNode(
                                node.id,
                                params.x + size / 2f,
                                params.y + size / 2f + layoutToScreenOffsetY
                            )
                        }
                        draggingMarker = false
                        // Tap: delete-mode only. Long-press opens adjust.
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
            text = s(R.string.action_close)
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
            toast(s(R.string.toast_popup_fail, title))
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
            s(R.string.node_interval_cycle)
        } else {
            s(R.string.node_interval)
        }
        showPanel(s(R.string.node_popup_title, live.order)) {
            addView(TextView(this@OverlayService).apply {
                text = s(R.string.node_rhythm_hint)
                setTextColor(Color.parseColor("#6E6E73"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, 0, 0, dp(6))
            })
            val (r1, etInterval) = fieldRow(intervalLabel, live.intervalToNextMs.toString())
            addView(r1)
            val (rTap, etTap) = fieldRow(s(R.string.node_tap_duration), live.tapDurationMs.toString())
            if (live.kind == NodeKind.CLICK) addView(rTap)
            val (r2, etClicks) = fieldRow(s(R.string.node_click_count), live.clickCount.toString())
            if (live.kind == NodeKind.CLICK) addView(r2)
            addView(TextView(this@OverlayService).apply {
                text = s(R.string.apply)
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#1D1D1F"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(12), 0, 0)
                setOnClickListener {
                    val interval = etInterval.text.toString().toLongOrNull()?.coerceAtLeast(0)
                        ?: live.intervalToNextMs
                    val taps = etTap.text.toString().toLongOrNull()?.coerceAtLeast(1)
                        ?: live.tapDurationMs
                    val clicks = etClicks.text.toString().toIntOrNull()?.coerceAtLeast(1)
                        ?: live.clickCount
                    AppStore.updateNode(live.id) {
                        it.copy(
                            intervalToNextMs = interval,
                            tapDurationMs = taps,
                            clickCount = clicks
                        )
                    }
                    dismissPopup()
                    toast(s(R.string.toast_node_updated, live.order))
                }
            })
        }
    }

    private fun showParamsPopup() {
        val p = AppStore.params.value
        showPanel(s(R.string.params_title)) {
            val (r1, etCycles) = fieldRow(
                s(R.string.params_cycles),
                if (p.cycleCount < 0) "" else p.cycleCount.toString()
            )
            addView(r1)
            val (r2, etStop) = fieldRow(s(R.string.params_stop_after), p.stopAfterMs.toString())
            addView(r2)
            val (r3, etTap) = fieldRow(s(R.string.params_global_tap), p.tapDurationMs.toString())
            addView(r3)
            addView(TextView(this@OverlayService).apply {
                text = s(R.string.params_speed_hint)
                setTextColor(Color.parseColor("#6E6E73"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, dp(4), 0, dp(8))
            })
            var jitter = p.jitter
            val jitterTv = TextView(this@OverlayService).apply {
                text = if (jitter) s(R.string.jitter_on) else s(R.string.jitter_off)
                setTextColor(Color.parseColor("#1D1D1F"))
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    jitter = !jitter
                    text = if (jitter) s(R.string.jitter_on) else s(R.string.jitter_off)
                }
            }
            addView(jitterTv)
            addView(TextView(this@OverlayService).apply {
                text = s(R.string.apply)
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
                    val tap = etTap.text.toString().toLongOrNull()?.coerceAtLeast(1) ?: p.tapDurationMs
                    AppStore.setParams(
                        p.copy(cycleCount = cycles, stopAfterMs = stop, jitter = jitter, tapDurationMs = tap)
                    )
                    dismissPopup()
                    toast(s(R.string.toast_params_applied, tap))
                }
            })
        }
    }

    private fun showSaveScheme() {
        showPanel(s(R.string.save_scheme_title)) {
            val et = EditText(this@OverlayService).apply {
                hint = s(R.string.scheme_name_hint)
                setText(AppStore.sessionType.value.label())
                setTextColor(Color.parseColor("#1D1D1F"))
                setHintTextColor(Color.parseColor("#8E8E93"))
                setBackgroundColor(Color.parseColor("#E5E5EA"))
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            addView(et)
            addView(TextView(this@OverlayService).apply {
                text = s(R.string.action_save)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1D1D1F"))
                setPadding(0, dp(12), 0, 0)
                setOnClickListener {
                    val name = et.text.toString().trim()
                    if (AppStore.saveScheme(name)) {
                        dismissPopup(); toast(s(R.string.toast_saved, name))
                    } else toast(s(R.string.toast_save_fail))
                }
            })
        }
    }

    private fun showUseScheme() {
        val names = AppStore.schemes.value.keys.sorted()
        if (names.isEmpty()) {
            toast(s(R.string.toast_no_schemes)); return
        }
        showPanel(s(R.string.use_scheme_title)) {
            names.forEach { name ->
                val scheme = AppStore.schemes.value[name]!!
                addView(TextView(this@OverlayService).apply {
                    text = s(
                        R.string.scheme_item,
                        name,
                        scheme.type.label(),
                        scheme.nodes.size
                    )
                    setTextColor(Color.parseColor("#1D1D1F"))
                    setPadding(0, dp(10), 0, dp(10))
                    setOnClickListener {
                        AppStore.useScheme(name)
                        dismissPopup()
                        markersDirty = true
                        rebuildBar()
                        toast(s(R.string.toast_loaded, name))
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
