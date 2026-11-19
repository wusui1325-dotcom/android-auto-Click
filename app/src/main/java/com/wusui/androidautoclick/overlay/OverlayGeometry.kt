package com.wusui.androidautoclick.overlay

/**
 * Pure layout math for the floating control bar, markers, and popups.
 * LayoutParams (x, y) plus a layout-to-screen offset equals on-screen
 * (getLocationOnScreen / dispatchGesture) coordinates.
 *
 * Stored scheme/node coordinates stay in screen pixels and are never
 * rotation-matrix remapped; only the control bar is clamped on-screen
 * (and into the display cutout safe area when insets are provided).
 */
data class ScreenSize(val width: Int, val height: Int) {
    fun containsPoint(x: Float, y: Float): Boolean =
        width > 0 && height > 0 && x >= 0f && y >= 0f && x < width && y < height
}

data class LayoutOffset(val x: Int = 0, val y: Int = 0)

data class PanelLayout(val x: Int, val y: Int, val width: Int, val maxHeight: Int)

/** Display cutout / safe-area insets in screen pixels (all >= 0). */
data class CutoutInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

object OverlayGeometry {

    /**
     * Prefer a live, rotation-aware display size over context window metrics.
     * Window metrics / Resources can stay in portrait on an Application context.
     */
    fun preferLiveSize(
        displayW: Int,
        displayH: Int,
        fallbackW: Int,
        fallbackH: Int
    ): Pair<Int, Int> {
        if (displayW > 0 && displayH > 0) return displayW to displayH
        if (fallbackW > 0 && fallbackH > 0) return fallbackW to fallbackH
        return 1 to 1
    }

    fun offsetFromLocation(
        locationX: Int,
        locationY: Int,
        paramsX: Int,
        paramsY: Int
    ): LayoutOffset = LayoutOffset(locationX - paramsX, locationY - paramsY)

    /** Marker / window center in screen pixels from overlay LayoutParams. */
    fun screenFromParams(
        paramsX: Int,
        paramsY: Int,
        halfSize: Float,
        offset: LayoutOffset
    ): Pair<Float, Float> =
        paramsX + halfSize + offset.x to paramsY + halfSize + offset.y

    /** Overlay LayoutParams top-left from a screen-space center. */
    fun paramsFromScreen(
        screenX: Float,
        screenY: Float,
        halfSize: Float,
        offset: LayoutOffset
    ): Pair<Int, Int> =
        (screenX - halfSize - offset.x).toInt() to (screenY - halfSize - offset.y).toInt()

    /**
     * Marker center in screen pixels from [View.getLocationOnScreen].
     * Prefer this when committing a drag: it does not depend on [LayoutOffset],
     * which may still be uncalibrated or may have changed mid-gesture.
     */
    fun screenFromLocation(
        locationX: Int,
        locationY: Int,
        halfSize: Float
    ): Pair<Float, Float> =
        locationX + halfSize to locationY + halfSize

    /** Finger movement past touch slop counts as a drag, not a tap. */
    fun exceededSlop(dx: Float, dy: Float, slop: Int): Boolean {
        val s = slop.coerceAtLeast(0).toFloat()
        return kotlin.math.abs(dx) > s || kotlin.math.abs(dy) > s
    }

    /**
     * Keep as much of a [barW] x [barH] window in the safe area as possible.
     * [centerX]/[centerY] and the result are in the same space as LayoutParams
     * (convert to/from screen with [offset]). [safeLeft]/[safeTop]/[safeRight]/
     * [safeBottom] are cutout insets in screen pixels. If the bar is larger than
     * the safe area, pin the origin to the safe-area start so the leading edge
     * stays tappable.
     */
    fun clampBarCenter(
        centerX: Float,
        centerY: Float,
        barW: Int,
        barH: Int,
        screenW: Int,
        screenH: Int,
        offset: LayoutOffset = LayoutOffset(),
        safeLeft: Int = 0,
        safeTop: Int = 0,
        safeRight: Int = 0,
        safeBottom: Int = 0
    ): Pair<Float, Float> {
        val w = barW.coerceAtLeast(1)
        val h = barH.coerceAtLeast(1)
        val visCx = centerX + offset.x
        val visCy = centerY + offset.y
        val left = safeLeft.coerceAtLeast(0).coerceAtMost(screenW.coerceAtLeast(0))
        val top = safeTop.coerceAtLeast(0).coerceAtMost(screenH.coerceAtLeast(0))
        val hiX = (screenW - safeRight.coerceAtLeast(0)).coerceAtLeast(left)
        val hiY = (screenH - safeBottom.coerceAtLeast(0)).coerceAtLeast(top)
        val originX = clampOrigin(visCx - w / 2f, w, left, hiX)
        val originY = clampOrigin(visCy - h / 2f, h, top, hiY)
        return (originX + w / 2f - offset.x) to (originY + h / 2f - offset.y)
    }

    /** Clamp [origin] so a window of [size] stays in `[0, screen)`. */
    fun clampOrigin(origin: Float, size: Int, screen: Int): Float =
        clampOrigin(origin, size, 0, screen)

    /**
     * Clamp [origin] so a window of [size] stays in `[lo, hi)`.
     * If the window is larger than the span, pin to [lo] so the start stays tappable.
     */
    fun clampOrigin(origin: Float, size: Int, lo: Int, hi: Int): Float {
        if (hi <= lo) return origin
        val available = hi - lo
        return if (size >= available) lo.toFloat() else origin.coerceIn(lo.toFloat(), (hi - size).toFloat())
    }

    fun countPointsOutside(points: Iterable<Pair<Float, Float>>, screen: ScreenSize): Int =
        points.count { !screen.containsPoint(it.first, it.second) }

    /**
     * Popup placement: ~82% width, preferred top at 22% of height, height capped
     * so the panel stays on-screen (caller puts content in a ScrollView).
     */
    fun panelLayout(
        screenW: Int,
        screenH: Int,
        widthFraction: Float = 0.82f,
        yFraction: Float = 0.22f,
        maxHeightFraction: Float = 0.70f,
        minMargin: Int = 8
    ): PanelLayout {
        val sw = screenW.coerceAtLeast(1)
        val sh = screenH.coerceAtLeast(1)
        val margin = minMargin.coerceAtLeast(0).coerceAtMost((sh / 4).coerceAtLeast(0))
        val maxWidth = (sw - 2 * margin).coerceAtLeast(1)
        val width = (sw * widthFraction).toInt().coerceIn(1, maxWidth)
        val x = ((sw - width) / 2).coerceAtLeast(0)
        val preferredMaxH = (sh * maxHeightFraction).toInt().coerceAtLeast(1)
        val available = (sh - 2 * margin).coerceAtLeast(1)
        val maxH = preferredMaxH.coerceAtMost(available)
        var y = (sh * yFraction).toInt()
        if (y < margin) y = margin
        if (y + maxH + margin > sh) {
            y = (sh - maxH - margin).coerceAtLeast(0)
        }
        return PanelLayout(x = x, y = y, width = width, maxHeight = maxH)
    }
}
