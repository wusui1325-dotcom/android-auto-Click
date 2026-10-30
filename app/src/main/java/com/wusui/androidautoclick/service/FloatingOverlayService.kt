package com.wusui.androidautoclick.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import com.wusui.androidautoclick.ClickController

/**
 * 可拖曳的懸浮控制按鈕。
 * 需要 SYSTEM_ALERT_WINDOW 權限。
 */
class FloatingOverlayService : Service() {

    companion object {
        var isRunning = false
            private set

        const val ACTION_SHOW = "show"
        const val ACTION_HIDE = "hide"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (ClickController.isRunning) {
                updateButtonText()
                refreshHandler.postDelayed(this, 300)
            }
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
                removeOverlay()
                stopSelf()
            }
            else -> showOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val size = dp(64)
        val view = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
        }

        val button = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            gravity = android.view.Gravity.CENTER
            text = if (ClickController.isRunning) "■" else "▶"
            setTextColor(Color.parseColor("#F5F5F7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1C1C1E"))
                setStroke(dp(1), Color.parseColor("#2C2C2E"))
            }
            setOnClickListener { toggleClicking() }
        }

        view.addView(button)

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        setupDrag(view, button, params)

        try {
            windowManager.addView(view, params)
            overlayView = view
            layoutParams = params
        } catch (_: Exception) {
            // 權限不足或已存在
        }
    }

    private fun toggleClicking() {
        ClickController.toggle()
        if (ClickController.isRunning) {
            refreshHandler.post(refreshRunnable)
        } else {
            refreshHandler.removeCallbacks(refreshRunnable)
        }
        updateButtonText()
    }

    private fun updateButtonText() {
        val button = (overlayView as? FrameLayout)?.getChildAt(0) as? TextView ?: return
        button.text = if (ClickController.isRunning) {
            val remain = ClickController.remainingDisplay.ifEmpty { "■" }
            val pt = if (ClickController.points.size > 1) "P${ClickController.currentPointIndex + 1}" else ""
            if (pt.isNotEmpty()) "$remain\n$pt" else remain
        } else {
            "▶"
        }
    }

    private fun setupDrag(container: View, button: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) button.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        layoutParams = null
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (ClickController.isRunning) ClickController.stop()
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
