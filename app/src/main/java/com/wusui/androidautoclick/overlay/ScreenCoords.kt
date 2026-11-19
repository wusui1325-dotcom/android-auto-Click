package com.wusui.androidautoclick.overlay

/**
 * Converts between current screen-space pixels
 * ([android.view.View.getLocationOnScreen] / Accessibility dispatchGesture)
 * and the display's natural (ROTATION_0) axis space used by a touchscreen
 * digitizer or a virtual uinput device with a stable ABS range.
 *
 * Android InputReader applies the display rotation on top of a DIRECT
 * touchscreen's raw axes (AOSP TouchInputMapper / input_coordinates.md).
 * If the virtual device's ABS range is the *already rotated* screen size
 * and injected points are also in screen space, landscape / 180° / left-right
 * flips land in the wrong place. Keep ABS in natural pixels and convert here.
 *
 * Mapping matches the orientation-aware DIRECT path:
 * ROTATION_90 is the 90° counter-clockwise case (raw (0, y) → screen (y, W-1)).
 * Transforms use (nativeSize - 1) so discrete corners stay on-pixel.
 *
 * [rotation] is [android.view.Surface.getRotation]: 0, 1 (90), 2 (180), 3 (270).
 * Stored scheme coordinates stay in screen pixels of the rotation they were
 * recorded in and are never remapped by this helper.
 */
object ScreenCoords {
    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3

    fun normalizeRotation(rotation: Int): Int {
        val r = rotation % 4
        return if (r < 0) r + 4 else r
    }

    fun isLandscapeRotation(rotation: Int): Boolean {
        val r = normalizeRotation(rotation)
        return r == ROTATION_90 || r == ROTATION_270
    }

    /**
     * Natural (ROTATION_0) pixel size from the current [Display.getRealSize]
     * and [rotation].
     */
    fun nativeSize(screenW: Int, screenH: Int, rotation: Int): Pair<Int, Int> {
        val w = screenW.coerceAtLeast(1)
        val h = screenH.coerceAtLeast(1)
        return if (isLandscapeRotation(rotation)) h to w else w to h
    }

    /**
     * Screen-space point → natural axis space of a ROTATION_0 ABS range
     * of [nativeW] × [nativeH].
     */
    fun screenToNative(
        x: Float,
        y: Float,
        nativeW: Int,
        nativeH: Int,
        rotation: Int
    ): Pair<Float, Float> {
        val w = (nativeW.coerceAtLeast(1) - 1).toFloat()
        val h = (nativeH.coerceAtLeast(1) - 1).toFloat()
        return when (normalizeRotation(rotation)) {
            ROTATION_90 -> (w - y) to x
            ROTATION_180 -> (w - x) to (h - y)
            ROTATION_270 -> y to (h - x)
            else -> x to y
        }
    }

    /** Inverse of [screenToNative]. */
    fun nativeToScreen(
        x: Float,
        y: Float,
        nativeW: Int,
        nativeH: Int,
        rotation: Int
    ): Pair<Float, Float> {
        val w = (nativeW.coerceAtLeast(1) - 1).toFloat()
        val h = (nativeH.coerceAtLeast(1) - 1).toFloat()
        return when (normalizeRotation(rotation)) {
            ROTATION_90 -> y to (w - x)
            ROTATION_180 -> (w - x) to (h - y)
            ROTATION_270 -> (h - y) to x
            else -> x to y
        }
    }

    /**
     * Screen-space point → natural axis, deriving native size from the
     * current screen size and [rotation].
     */
    fun screenToNativeFromScreen(
        x: Float,
        y: Float,
        screenW: Int,
        screenH: Int,
        rotation: Int
    ): Pair<Float, Float> {
        val (nw, nh) = nativeSize(screenW, screenH, rotation)
        return screenToNative(x, y, nw, nh, rotation)
    }

    fun nativeToScreenFromScreen(
        x: Float,
        y: Float,
        screenW: Int,
        screenH: Int,
        rotation: Int
    ): Pair<Float, Float> {
        val (nw, nh) = nativeSize(screenW, screenH, rotation)
        return nativeToScreen(x, y, nw, nh, rotation)
    }
}
