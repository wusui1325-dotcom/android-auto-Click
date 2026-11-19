package com.wusui.androidautoclick.overlay

/**
 * Pure layout math for the floating control bar, markers, and popups.
 * LayoutParams (x, y) plus a layout-to-screen offset equals on-screen
 * (getLocationOnScreen / dispatchGesture) coordinates.
 *
 * Stored scheme/node coordinates stay in screen pixels and are never
 * rotation-matrix remapped; only the control bar is clamped on-screen.
 */
data class ScreenSize(val width: Int, val height: Int) {
    fun containsPoint(x: Float, y: Float): Boolean =
        width > 0 && height > 0 && x >= 0f && y >= 0f && x < width && y < height
}

data class LayoutOffset(val x: Int = 0, val y: Int = 0)

data class PanelLayout(val x: Int, val y: Int, val width: Int, val maxHeight: Int)

object OverlayGeometry {

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
     * Keep as much of a [barW] x [barH] window on-screen as possible.
     * [centerX]/[centerY] and the result are in the same space as LayoutParams
     * (convert to/from screen with [offset]). If the bar is larger than the
     * screen, pin the origin to 0 so the start of the bar stays tappable.
     */
    fun clampBarCenter(
        centerX: Float,
        centerY: Float,
        barW: Int,
        barH: Int,
        screenW: Int,
        screenH: Int,
        offset: LayoutOffset = LayoutOffset()
    ): Pair<Float, Float> {
        val w = barW.coerceAtLeast(1)
        val h = barH.coerceAtLeast(1)
        val visCx = centerX + offset.x
        val visCy = centerY + offset.y
        val originX = clampOrigin(visCx - w / 2f, w, screenW)
        val originY = clampOrigin(visCy - h / 2f, h, screenH)
        return (originX + w / 2f - offset.x) to (originY + h / 2f - offset.y)
    }

    fun clampOrigin(origin: Float, size: Int, screen: Int): Float {
        if (screen <= 0) return origin
        return if (size >= screen) 0f else origin.coerceIn(0f, (screen - size).toFloat())
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
