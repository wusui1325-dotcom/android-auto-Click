package com.wusui.androidautoclick.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import com.wusui.androidautoclick.ClickController

/**
 * 可收放的懸浮控制面板，並在螢幕上顯示可拖拽的觸控點。
 * 視覺對齊個人中心：深灰底、細邊、無霓虹紅。
 */
class FloatingOverlayService : Service() {

    companion object {
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeAll()
                stopSelf()
            }
            else -> showControl()
        }
        return START_STICKY
    }

    private fun showControl() {
        if (controlView != null) {
            refreshMarkers()
            return
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
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
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
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
        val base = if (ClickController.isRunning) "■" else "▶"
        return if (ClickController.showCoordsOnOverlay && ClickController.points.isNotEmpty()) {
            val p = ClickController.points.first()
            "$base\n${p.first.toInt()}"
        } else base
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
            val b = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#F5F5F7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener { action() }
            }
            col.addView(b)
        }
        addBtn(if (ClickController.isRunning) "停止" else "開始") {
            toggleClicking()
            rebuildControl()
        }
        addBtn("新增點") {
            ClickController.addPoint(540f, 960f)
            refreshMarkers()
        }
        addBtn("刪除最後") {
            ClickController.removeLastPoint()
            refreshMarkers()
        }
        addBtn(if (ClickController.showMarkers) "隱藏標記" else "顯示標記") {
            ClickController.showMarkers = !ClickController.showMarkers
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
        if (!ClickController.showMarkers) return
        ClickController.points.forEachIndexed { i, (x, y) ->
            addMarker(x, y, "${i + 1}") { nx, ny ->
                if (i < ClickController.points.size) ClickController.points[i] = nx to ny
            }
        }
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
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x.toInt() - size / 2
            this.y = y.toInt() - size / 2
        }
        var ix = 0
        var iy = 0
        var tx = 0f
        var ty = 0f
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ix + (e.rawX - tx).toInt()
                    params.y = iy + (e.rawY - ty).toInt()
                    try { windowManager.updateViewLayout(tv, params) } catch (_: Exception) {}
                    onMove(params.x + size / 2f, params.y + size / 2f)
                    true
                }
                else -> false
            }
        }
        try {
            windowManager.addView(tv, params)
            markers.add(tv to params)
        } catch (_: Exception) {
        }
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var ix = 0
        var iy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
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
        if (ClickController.isRunning) ClickController.stop() else ClickController.start()
        if (!expanded) rebuildControl()
    }

    private fun removeAll() {
        refreshHandler.removeCallbacks(refreshRunnable)
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
