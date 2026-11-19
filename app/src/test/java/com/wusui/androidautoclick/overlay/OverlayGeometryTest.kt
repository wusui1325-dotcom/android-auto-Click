package com.wusui.androidautoclick.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OverlayGeometryTest {

    @Test
    fun offsetFromLocation_isScreenMinusParams() {
        val offset = OverlayGeometry.offsetFromLocation(
            locationX = 10,
            locationY = 80,
            paramsX = 10,
            paramsY = 24
        )
        assertEquals(LayoutOffset(0, 56), offset)
    }

    @Test
    fun paramsAndScreen_roundTripWithinOnePixel() {
        val offset = LayoutOffset(x = 12, y = 56)
        val half = 22f
        val screenX = 540.4f
        val screenY = 1200.6f
        val (px, py) = OverlayGeometry.paramsFromScreen(screenX, screenY, half, offset)
        val (rx, ry) = OverlayGeometry.screenFromParams(px, py, half, offset)
        assertTrue(abs(rx - screenX) < 1f)
        assertTrue(abs(ry - screenY) < 1f)
    }

    @Test
    fun paramsFromScreen_appliesBothAxes() {
        val offset = LayoutOffset(x = 20, y = 40)
        val (x, y) = OverlayGeometry.paramsFromScreen(100f, 200f, halfSize = 10f, offset)
        assertEquals(70, x) // 100 - 10 - 20
        assertEquals(150, y) // 200 - 10 - 40
    }

    @Test
    fun screenFromParams_appliesBothAxes() {
        val offset = LayoutOffset(x = 20, y = 40)
        val (x, y) = OverlayGeometry.screenFromParams(70, 150, halfSize = 10f, offset)
        assertEquals(100f, x)
        assertEquals(200f, y)
    }

    @Test
    fun clampBarCenter_keepsBarFullyOnScreen() {
        val (cx, cy) = OverlayGeometry.clampBarCenter(
            centerX = 2000f,
            centerY = 3000f,
            barW = 200,
            barH = 80,
            screenW = 1080,
            screenH = 2400
        )
        assertEquals(980f, cx) // 1080 - 200/2
        assertEquals(2360f, cy) // 2400 - 80/2
    }

    @Test
    fun clampBarCenter_portraitYPastLandscapeHeight_isPulledOnScreen() {
        // Typical report: bar parked at portrait Y, then the phone rotates.
        val (cx, cy) = OverlayGeometry.clampBarCenter(
            centerX = 200f,
            centerY = 2000f,
            barW = 240,
            barH = 64,
            screenW = 2400,
            screenH = 1080
        )
        assertEquals(200f, cx)
        assertEquals(1048f, cy) // 1080 - 32
        val top = cy - 32f
        val bottom = cy + 32f
        assertTrue(top >= 0f)
        assertTrue(bottom <= 1080f)
    }

    @Test
    fun clampBarCenter_accountsForLayoutOffset() {
        val offset = LayoutOffset(x = 0, y = 48)
        val (cx, cy) = OverlayGeometry.clampBarCenter(
            centerX = 100f,
            centerY = 2000f,
            barW = 100,
            barH = 50,
            screenW = 800,
            screenH = 360,
            offset = offset
        )
        // visual center y = cy + 48 must be <= 360 - 25
        val visY = cy + offset.y
        assertTrue(visY + 25f <= 360f + 0.01f)
        assertTrue(visY - 25f >= -0.01f)
        assertEquals(100f, cx)
    }

    @Test
    fun clampBarCenter_wideBarPinsToOrigin() {
        val (cx, cy) = OverlayGeometry.clampBarCenter(
            centerX = 400f,
            centerY = 100f,
            barW = 2000,
            barH = 40,
            screenW = 1080,
            screenH = 2400
        )
        // origin pinned to 0, center = 1000
        assertEquals(1000f, cx)
        assertEquals(100f, cy)
    }

    @Test
    fun containsPoint_andCountOutside() {
        val screen = ScreenSize(1080, 2400)
        assertTrue(screen.containsPoint(0f, 0f))
        assertTrue(screen.containsPoint(1079f, 2399f))
        assertFalse(screen.containsPoint(1080f, 10f))
        assertFalse(screen.containsPoint(10f, 2400f))
        assertFalse(screen.containsPoint(-1f, 10f))
        val n = OverlayGeometry.countPointsOutside(
            listOf(100f to 100f, 2000f to 100f, 10f to 5000f, 50f to 50f),
            screen
        )
        assertEquals(2, n)
    }

    @Test
    fun panelLayout_landscapePhoneStaysOnShortEdge() {
        val layout = OverlayGeometry.panelLayout(
            screenW = 2400,
            screenH = 1080,
            minMargin = 8
        )
        assertEquals((2400 * 0.82f).toInt(), layout.width)
        assertTrue(layout.x >= 0)
        assertTrue(layout.x + layout.width <= 2400)
        assertTrue(layout.y >= 0)
        assertTrue(layout.maxHeight > 0)
        assertTrue(layout.y + layout.maxHeight <= 1080)
        // Must leave room on the short edge (old 82% width WRAP_CONTENT at y=22% could overflow).
        assertTrue(layout.maxHeight <= (1080 * 0.70f).toInt())
    }

    @Test
    fun panelLayout_portraitStillNearPreferredY() {
        val layout = OverlayGeometry.panelLayout(
            screenW = 1080,
            screenH = 2400,
            minMargin = 8
        )
        assertEquals((2400 * 0.22f).toInt(), layout.y)
        assertTrue(layout.maxHeight >= (2400 * 0.70f).toInt() - 1)
        assertTrue(layout.y + layout.maxHeight < 2400)
    }

    @Test
    fun preferLiveSize_usesDisplayEvenIfFallbackIsPortrait() {
        // Application window metrics often stay 1080x2400 after a landscape rotate.
        val (w, h) = OverlayGeometry.preferLiveSize(2400, 1080, 1080, 2400)
        assertEquals(2400, w)
        assertEquals(1080, h)
    }

    @Test
    fun preferLiveSize_fallsBackWhenDisplayUnavailable() {
        val (w, h) = OverlayGeometry.preferLiveSize(0, 0, 1080, 2400)
        assertEquals(1080, w)
        assertEquals(2400, h)
    }

    @Test
    fun preferLiveSize_lastResortIsOneByOne() {
        assertEquals(1 to 1, OverlayGeometry.preferLiveSize(0, -1, 0, 0))
    }

    @Test
    fun clampBarCenter_leftCutoutPinsBarOutOfNotch() {
        val (cx, cy) = OverlayGeometry.clampBarCenter(
            centerX = 40f,
            centerY = 200f,
            barW = 100,
            barH = 40,
            screenW = 2400,
            screenH = 1080,
            safeLeft = 80
        )
        // origin pinned to 80, center = 80 + 50
        assertEquals(130f, cx)
        assertEquals(200f, cy)
        assertTrue(cx - 50f >= 80f - 0.01f)
    }

    @Test
    fun clampBarCenter_rightCutoutKeepsBarOffCamera() {
        val (cx, _) = OverlayGeometry.clampBarCenter(
            centerX = 2390f,
            centerY = 200f,
            barW = 100,
            barH = 40,
            screenW = 2400,
            screenH = 1080,
            safeRight = 80
        )
        // origin max = 2400 - 80 - 100 = 2220, center = 2270
        assertEquals(2270f, cx)
        assertTrue(cx + 50f <= 2400f - 80f + 0.01f)
    }

    @Test
    fun clampBarCenter_accountsForLayoutOffsetAndCutout() {
        val offset = LayoutOffset(x = 0, y = 48)
        val (cx, cy) = OverlayGeometry.clampBarCenter(
            centerX = 100f,
            centerY = 2000f,
            barW = 100,
            barH = 50,
            screenW = 800,
            screenH = 360,
            offset = offset,
            safeTop = 20,
            safeBottom = 20
        )
        val visY = cy + offset.y
        assertTrue(visY - 25f >= 20f - 0.01f)
        assertTrue(visY + 25f <= 360f - 20f + 0.01f)
        assertEquals(100f, cx)
    }

    @Test
    fun clampBarCenter_barWiderThanSafeAreaPinsToSafeLeft() {
        val (cx, _) = OverlayGeometry.clampBarCenter(
            centerX = 400f,
            centerY = 100f,
            barW = 2000,
            barH = 40,
            screenW = 1080,
            screenH = 2400,
            safeLeft = 80,
            safeRight = 80
        )
        // origin pinned to 80, center = 80 + 1000
        assertEquals(1080f, cx)
    }
}
