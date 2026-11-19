package com.wusui.androidautoclick.overlay

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager

/**
 * Current real display size in the same pixel space as
 * [android.view.View.getLocationOnScreen] and Accessibility [dispatchGesture].
 *
 * Prefer [Display.getRealSize], which follows the current rotation, over
 * [WindowManager.getMaximumWindowMetrics] / Application [android.util.DisplayMetrics].
 * Those follow the *context* Configuration and can stay in portrait after the
 * phone has already rotated — especially when the caller only has an
 * Application context (Root CONFIG, default marker placement).
 */
object ScreenMetrics {

    fun realSize(context: Context): Pair<Int, Int> {
        val live = liveDisplaySize(context)
        val fallback = windowMetricsSize(context)
        return OverlayGeometry.preferLiveSize(live.first, live.second, fallback.first, fallback.second)
    }

    fun screenSize(context: Context): ScreenSize {
        val (w, h) = realSize(context)
        return ScreenSize(w, h)
    }

    /** Current [Display.getRotation], or [Surface.ROTATION_0] if unknown. */
    fun rotation(context: Context): Int {
        return try {
            displayOf(context)?.rotation ?: Surface.ROTATION_0
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    /** Safe-area insets from the current [Display.cutout], or zeros if none. */
    fun cutoutInsets(context: Context): CutoutInsets {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return CutoutInsets()
        return try {
            val cutout = displayOf(context)?.cutout ?: return CutoutInsets()
            CutoutInsets(
                left = cutout.safeInsetLeft.coerceAtLeast(0),
                top = cutout.safeInsetTop.coerceAtLeast(0),
                right = cutout.safeInsetRight.coerceAtLeast(0),
                bottom = cutout.safeInsetBottom.coerceAtLeast(0)
            )
        } catch (_: Exception) {
            CutoutInsets()
        }
    }

    /**
     * Live default display, not a stale Application window configuration.
     * A visual context's [Context.getDisplay] is tried first; otherwise
     * [Display.DEFAULT_DISPLAY] from DisplayManager.
     */
    private fun displayOf(context: Context): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.let { return it }
            } catch (_: Exception) {
            }
        }
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.let { return it }
        } catch (_: Exception) {
        }
        return try {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        } catch (_: Exception) {
            null
        }
    }

    private fun liveDisplaySize(context: Context): Pair<Int, Int> {
        val display = displayOf(context) ?: return 0 to 0
        val point = Point()
        return try {
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            point.x to point.y
        } catch (_: Exception) {
            0 to 0
        }
    }

    /** Last-resort size from the context WindowManager / Resources. */
    private fun windowMetricsSize(context: Context): Pair<Int, Int> {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val bounds = wm.currentWindowMetrics.bounds
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        return bounds.width() to bounds.height()
                    }
                } catch (_: Exception) {
                }
                try {
                    val bounds = wm.maximumWindowMetrics.bounds
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        return bounds.width() to bounds.height()
                    }
                } catch (_: Exception) {
                }
            }
            val dm = context.resources.displayMetrics
            return dm.widthPixels to dm.heightPixels
        } catch (_: Exception) {
            return 0 to 0
        }
    }
}
