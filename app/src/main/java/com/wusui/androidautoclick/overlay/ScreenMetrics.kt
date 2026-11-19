package com.wusui.androidautoclick.overlay

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/**
 * Current real display size in the same pixel space as
 * [android.view.View.getLocationOnScreen] and Accessibility [dispatchGesture].
 * Do not use a stale Application [android.util.DisplayMetrics] snapshot —
 * that is often the portrait size even after the phone has rotated.
 */
object ScreenMetrics {

    fun realSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        val point = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(point)
        if (point.x > 0 && point.y > 0) return point.x to point.y
        val dm = context.resources.displayMetrics
        return dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
    }

    fun screenSize(context: Context): ScreenSize {
        val (w, h) = realSize(context)
        return ScreenSize(w, h)
    }
}
