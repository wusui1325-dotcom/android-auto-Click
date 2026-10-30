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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ServiceCompat
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.engine.ClickEngine

/**
 * Floating control + draggable markers.
 *
 * Official contracts followed:
 * - TYPE_APPLICATION_OVERLAY (API 26+)
 * - FGS specialUse via ServiceCompat.startForeground + FOREGROUND_SERVICE_TYPE_SPECIAL_USE
 * - layoutInDisplayCutoutMode ALWAYS so overlay coords can match full screen pixels
 *   used by GestureDescription (screen pixels)
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
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val persistHandler = Handler(Looper.getMainLooper())
    /** screenY - layoutParams.y for view top edge (status/cutout OEM delta). */
    private var layoutToScreenOffsetY = 0
    private var offsetCalibrated = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateCollapsedGlyph()
            refreshHandler.postDelayed(this, 400)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
        startAsForeground()
    }

    private fun startAsForeground() {
        val notification = ClickNotification.buildOverlayKeepAlive(this)
        // Official: ServiceCompat.startForeground with typed FGS (core 1.12+)
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
            // Floating overlay needs full-screen pixel space so marker centers == gesture coords
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // ALWAYS is preferred for cutout-edge overlays when available (API 30+)
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Do not fit away from system bars — keep layout origin aligned to screen
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
                refreshMarkers()
            } catch (_: Exception) {
            }
        }
    }

    /** Current screen size in pixels (WindowMetrics on R+, else DisplayMetrics). */
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
            refreshMarkers()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        rebuildControl()
        refreshMarkers()
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.post(refreshRunnable)
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
        setupDrag(container, params)
        try {
            windowManager.addView(container, params)
            controlView = container
            controlParams = params
        } catch (_: Exception) {
        }
    }

    private fun buildCollapsed(): View {
        val size = dp(56)
        val btn = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            gravity = Gravity.CENTER
            text = glyph()
            setTextColor(Color.parseColor("#F5F5F7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            background = ovalBg()
            tag = "glyph"
            setOnClickListener { toggleClicking() }
        }
        val wrap = FrameLayout(this)
        wrap.addView(btn)
        wrap.setOnLongClickListener {
            expanded = true
            rebuildControl()
            true
        }
        return wrap
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
        if (tv.text.toString() != next) tv.text = next
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
        }
        fun addBtn(label: String, action: () -> Unit) {
            col.addView(TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#F5F5F7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener { action() }
            })
        }
        val st = ClickEngine.status.value
        addBtn(if (st.running || st.replaying) "停止" else "開始") {
            toggleClicking()
            rebuildControl()
        }
        addBtn("新增點") {
            val (w, h) = screenSize()
            SettingsRepository.addPoint(w / 2f, h / 2f)
            refreshMarkers()
        }
        addBtn("刪除最後") {
            SettingsRepository.removeLastPoint()
            refreshMarkers()
        }
        val show = SettingsRepository.config.value.showMarkers
        addBtn(if (show) "隱藏標記" else "顯示標記") {
            SettingsRepository.updateConfig { it.copy(showMarkers = !it.showMarkers) }
            refreshMarkers()
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
        if (!SettingsRepository.config.value.showMarkers) return
        SettingsRepository.points.value.forEachIndexed { i, p ->
            addMarker(p.x, p.y, "${i + 1}") { nx, ny ->
                SettingsRepository.updatePoint(i, nx, ny)
            }
        }
    }

    private fun schedulePersist() {
        persistHandler.removeCallbacksAndMessages(null)
        persistHandler.postDelayed({ /* updatePoint already persists */ }, 300)
    }

    private fun addMarker(x: Float, y: Float, label: String, onMove: (Float, Float) -> Unit) {
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
        }
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ix + (e.rawX - tx).toInt()
                    params.y = iy + (e.rawY - ty).toInt()
                    try { windowManager.updateViewLayout(tv, params) } catch (_: Exception) {}
                    // rawX/rawY are absolute screen coords — same space as GestureDescription
                    onMove(e.rawX, e.rawY)
                    schedulePersist()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onMove(layoutToScreenX(params.x, size), layoutToScreenY(params.y, size))
                    schedulePersist()
                    false
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

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                    params.x = ix + dx
                    params.y = iy + dy
                    try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> !moved
                else -> false
            }
        }
    }

    private fun toggleClicking() {
        ClickEngine.toggle()
        if (!expanded) rebuildControl()
    }

    private fun removeAll() {
        refreshHandler.removeCallbacks(refreshRunnable)
        persistHandler.removeCallbacksAndMessages(null)
        markers.forEach { try { windowManager.removeView(it.first) } catch (_: Exception) {} }
        markers.clear()
        controlView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        controlView = null
    }

    override fun onDestroy() {
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
