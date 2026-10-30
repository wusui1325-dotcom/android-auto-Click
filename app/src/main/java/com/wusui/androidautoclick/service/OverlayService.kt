package com.wusui.androidautoclick.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.engine.ClickEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Floating control + draggable markers.
 *
 * Official contracts followed:
 * - TYPE_APPLICATION_OVERLAY (API 26+)
 * - FGS specialUse via ServiceCompat.startForeground + FOREGROUND_SERVICE_TYPE_SPECIAL_USE
 * - layoutInDisplayCutoutMode ALWAYS so overlay coords can match full screen pixels
 * - setFitInsetsTypes(0) so the window is not fitted away from system bars / cutout
 * - Marker screen position calibrated with getLocationOnScreen after first layout
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        var isRunning = false
            private set
        const val ACTION_SHOW = "show"
        const val ACTION_HIDE = "hide"
    }

    private lateinit var windowManager: WindowManager
    private var controlView: View? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private val markers = mutableListOf<Pair<View, WindowManager.LayoutParams>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val persistHandler = Handler(Looper.getMainLooper())
    private var layoutToScreenOffsetY = 0
    private var offsetCalibrated = false
    private var lastGlyph = ""
    private var scope: CoroutineScope? = null
    private var observeJob: Job? = null
    private var markersDirty = true
    private var draggingMarker = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateCollapsedGlyph()
            if (markersDirty && !draggingMarker) {
                markersDirty = false
                refreshMarkers()
            }
            mainHandler.postDelayed(this, 800)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        startAsForeground()
        observeRepo()
    }

    private fun observeRepo() {
        observeJob?.cancel()
        observeJob = scope?.launch {
            launch {
                combine(
                    SettingsRepository.points,
                    SettingsRepository.config
                ) { pts, cfg -> pts.size to (cfg.showMarkers to cfg.lockMarkersWhileRunning) }
                    .collect { markersDirty = true }
            }
            launch {
                ClickEngine.status.collect {
                    updateCollapsedGlyph()
                    markersDirty = true // lock/unlock markers while active
                }
            }
        }
    }

    private fun startAsForeground() {
        val notification = ClickNotification.buildOverlayKeepAlive(this)
        ServiceCompat.startForeground(
            this,
            ClickNotification.OVERLAY_NOTIFY_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeAll()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                startAsForeground()
                showControl()
            }
        }
        return START_STICKY
    }

    private fun baseOverlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
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

    private fun screenToLayoutX(screenX: Float, size: Int): Int = (screenX - size / 2f).toInt()
    private fun screenToLayoutY(screenY: Float, size: Int): Int =
        (screenY - size / 2f - layoutToScreenOffsetY).toInt()
    private fun layoutToScreenX(layoutX: Int, size: Int): Float = layoutX + size / 2f
    private fun layoutToScreenY(layoutY: Int, size: Int): Float =
        layoutY + size / 2f + layoutToScreenOffsetY

    private fun calibrateOffsetIfNeeded(view: View, params: WindowManager.LayoutParams) {
        if (offsetCalibrated) return
        view.post {
            try {
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                layoutToScreenOffsetY = loc[1] - params.y
                offsetCalibrated = true
                markersDirty = true
            } catch (_: Exception) {
            }
        }
    }

    fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val d = resources.displayMetrics
            d.widthPixels to d.heightPixels
        }
    }

    private fun showControl() {
        if (controlView != null) {
            markersDirty = true
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        rebuildControl()
        markersDirty = true
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    private fun rebuildControl() {
        controlView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        val container = if (expanded) buildExpanded() else buildCollapsed()
        val params = baseOverlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            x = controlParams?.x ?: dp(16)
            y = controlParams?.y ?: dp(120)
        }
        setupDragOrTap(container, params, onTap = {
            if (expanded) {
                // taps handled by child buttons
            } else {
                toggleClicking()
            }
        }, onLongPress = {
            if (!expanded) {
                expanded = true
                rebuildControl()
            }
        })
        try {
            windowManager.addView(container, params)
            controlView = container
            controlParams = params
        } catch (_: Exception) {
        }
    }

    private fun buildCollapsed(): View {
        val size = dp(56)
        lastGlyph = glyph()
        val btn = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            gravity = Gravity.CENTER
            text = lastGlyph
            setTextColor(Color.parseColor("#F5F5F7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = ovalBg()
            tag = "glyph"
            // Clicks go through parent drag/tap handler → performClick path.
            isClickable = false
            isFocusable = false
        }
        return FrameLayout(this).apply { addView(btn) }
    }

    private fun glyph(): String {
        val st = ClickEngine.status.value
        return if (st.running || st.replaying) "■" else "▶"
    }

    private fun updateCollapsedGlyph() {
        if (expanded) return
        val root = controlView as? FrameLayout ?: return
        val tv = root.findViewWithTag<TextView>("glyph") ?: return
        val next = glyph()
        if (tv.text.toString() != next) {
            lastGlyph = next
            tv.text = next
        }
    }

    private fun buildExpanded(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#111111"))
                setStroke(dp(1), Color.parseColor("#1C1C1E"))
            }
            minimumWidth = dp(148)
        }
        fun addBtn(label: String, muted: Boolean = false, action: () -> Unit) {
            col.addView(TextView(this).apply {
                text = label
                setTextColor(Color.parseColor(if (muted) "#8E8E93" else "#F5F5F7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (muted) 13f else 15f)
                setPadding(dp(12), dp(if (muted) 6 else 10), dp(12), dp(if (muted) 6 else 10))
                setOnClickListener { action() }
            })
        }
        val st = ClickEngine.status.value
        val cfg = SettingsRepository.config.value
        val compact = cfg.compactOverlay

        addBtn(if (st.active) "停止" else "開始") {
            ClickEngine.toggle(fromOverlay = true)
            rebuildControl()
        }

        // Quick sequence switch (top N saved)
        val saved = SettingsRepository.savedSequences.value.keys.sorted()
        if (saved.isNotEmpty()) {
            addBtn("— 序列快捷 —", muted = true) {}
            val limit = if (compact) 4 else 8
            saved.take(limit).forEach { name ->
                val mark = if (name == cfg.activeSequenceName) "● " else "○ "
                addBtn("$mark$name") {
                    if (SettingsRepository.switchToSavedSequence(name)) {
                        ClickEngine.clearError()
                        rebuildControl()
                    }
                }
            }
        }

        if (!compact) {
            addBtn("新增點") {
                val (w, h) = screenSize()
                SettingsRepository.addPoint(w / 2f, h / 2f)
                markersDirty = true
            }
            addBtn("刪除最後") {
                SettingsRepository.removeLastPoint()
                markersDirty = true
            }
            addBtn("標記→序列步") {
                ClickEngine.importPointsAsSequence(300)
            }
        } else {
            addBtn("＋點") {
                val (w, h) = screenSize()
                SettingsRepository.addPoint(w / 2f, h / 2f)
                markersDirty = true
            }
        }

        val show = cfg.showMarkers
        addBtn(if (show) "隱藏標記" else "顯示標記") {
            SettingsRepository.updateConfig { it.copy(showMarkers = !it.showMarkers) }
            markersDirty = true
            rebuildControl()
        }
        addBtn("收起") {
            expanded = false
            rebuildControl()
        }
        return col
    }

    private fun refreshMarkers() {
        markers.forEach { try { windowManager.removeView(it.first) } catch (_: Exception) {} }
        markers.clear()
        val cfg = SettingsRepository.config.value
        if (!cfg.showMarkers) return
        val active = ClickEngine.status.value.active
        if (active && cfg.lockMarkersWhileRunning) {
            // Show dim locked markers (not touchable) so user still sees targets
            SettingsRepository.points.value.forEachIndexed { i, p ->
                addMarker(p.x, p.y, "${i + 1}", i, locked = true)
            }
            return
        }
        SettingsRepository.points.value.forEachIndexed { i, p ->
            addMarker(p.x, p.y, "${i + 1}", i, locked = false)
        }
    }

    private fun schedulePersist() {
        persistHandler.removeCallbacksAndMessages(null)
        persistHandler.postDelayed({
            SettingsRepository.persistPointsNow()
        }, 280)
    }

    private fun addMarker(x: Float, y: Float, label: String, index: Int, locked: Boolean = false) {
        val size = dp(36)
        val tv = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            gravity = Gravity.CENTER
            text = label
            setTextColor(Color.parseColor("#F5F5F7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3A3A3C"))
                setStroke(dp(1), Color.parseColor("#8E8E93"))
            }
        }
        val params = baseOverlayParams(size, size).apply {
            this.x = screenToLayoutX(x, size)
            this.y = screenToLayoutY(y, size)
            if (locked) {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
        }
        if (locked) {
            tv.alpha = 0.45f
            try {
                windowManager.addView(tv, params)
                markers.add(tv to params)
            } catch (_: Exception) {
            }
            return
        }
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    draggingMarker = true
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ix + (e.rawX - tx).toInt()
                    params.y = iy + (e.rawY - ty).toInt()
                    try { windowManager.updateViewLayout(tv, params) } catch (_: Exception) {}
                    // Always persist marker CENTER, not finger raw position.
                    val cx = layoutToScreenX(params.x, size)
                    val cy = layoutToScreenY(params.y, size)
                    SettingsRepository.updatePoint(index, cx, cy, persist = false)
                    schedulePersist()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val cx = layoutToScreenX(params.x, size)
                    val cy = layoutToScreenY(params.y, size)
                    SettingsRepository.updatePoint(index, cx, cy, persist = true)
                    draggingMarker = false
                    true
                }
                else -> false
            }
        }
        try {
            windowManager.addView(tv, params)
            markers.add(tv to params)
            calibrateOffsetIfNeeded(tv, params)
        } catch (_: Exception) {
        }
    }

    /**
     * Drag without swallowing taps / long-press.
     * Bugfix: previous OnTouchListener returned true on DOWN and never called
     * performClick, so short-press start/stop and long-press expand were dead.
     */
    private fun setupDragOrTap(
        view: View,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit,
        onLongPress: () -> Unit
    ) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val longTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        var moved = false
        var longFired = false
        val longRunnable = Runnable {
            if (!moved) {
                longFired = true
                onLongPress()
            }
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY
                    moved = false; longFired = false
                    mainHandler.postDelayed(longRunnable, longTimeout)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        if (!moved) {
                            moved = true
                            mainHandler.removeCallbacks(longRunnable)
                        }
                        params.x = ix + dx
                        params.y = iy + dy
                        try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longRunnable)
                    if (!moved && !longFired) {
                        // Prefer child click if hit a clickable child (expanded menu).
                        if (!dispatchClickToChild(v, e.rawX, e.rawY)) {
                            onTap()
                            if (!expanded) rebuildControl()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun dispatchClickToChild(root: View, rawX: Float, rawY: Float): Boolean {
        if (root !is android.view.ViewGroup) return false
        val loc = IntArray(2)
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (!child.isShown) continue
            child.getLocationOnScreen(loc)
            val l = loc[0]; val t = loc[1]
            val r = l + child.width; val b = t + child.height
            if (rawX >= l && rawX < r && rawY >= t && rawY < b) {
                if (child.hasOnClickListeners()) {
                    child.performClick()
                    return true
                }
                if (child is android.view.ViewGroup && dispatchClickToChild(child, rawX, rawY)) {
                    return true
                }
            }
        }
        return false
    }

    private fun toggleClicking() {
        ClickEngine.toggle(fromOverlay = true)
        if (!expanded) rebuildControl()
    }

    private fun removeAll() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.removeCallbacksAndMessages(null)
        persistHandler.removeCallbacksAndMessages(null)
        markers.forEach { try { windowManager.removeView(it.first) } catch (_: Exception) {} }
        markers.clear()
        controlView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        controlView = null
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scope?.cancel()
        removeAll()
        isRunning = false
        super.onDestroy()
    }

    private fun ovalBg() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#1C1C1E"))
        setStroke(dp(1), Color.parseColor("#2C2C2E"))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
