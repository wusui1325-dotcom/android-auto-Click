package com.wusui.androidautoclick.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScreenCoordsTest {

    private val nativeW = 1080
    private val nativeH = 2400

    @Test
    fun nativeSize_followsRotation() {
        assertEquals(1080 to 2400, ScreenCoords.nativeSize(1080, 2400, ScreenCoords.ROTATION_0))
        assertEquals(1080 to 2400, ScreenCoords.nativeSize(2400, 1080, ScreenCoords.ROTATION_90))
        assertEquals(1080 to 2400, ScreenCoords.nativeSize(1080, 2400, ScreenCoords.ROTATION_180))
        assertEquals(1080 to 2400, ScreenCoords.nativeSize(2400, 1080, ScreenCoords.ROTATION_270))
        assertEquals(1080 to 2400, ScreenCoords.nativeSize(2400, 1080, 5)) // 5 % 4 = 1
    }

    @Test
    fun isLandscapeRotation() {
        assertFalse(ScreenCoords.isLandscapeRotation(ScreenCoords.ROTATION_0))
        assertTrue(ScreenCoords.isLandscapeRotation(ScreenCoords.ROTATION_90))
        assertFalse(ScreenCoords.isLandscapeRotation(ScreenCoords.ROTATION_180))
        assertTrue(ScreenCoords.isLandscapeRotation(ScreenCoords.ROTATION_270))
    }

    @Test
    fun rotation0_isIdentity() {
        val (x, y) = ScreenCoords.screenToNative(100f, 200f, nativeW, nativeH, ScreenCoords.ROTATION_0)
        assertEquals(100f, x)
        assertEquals(200f, y)
    }

    @Test
    fun rotation90_mapsTopLeftScreenToNativeTopRightBand() {
        // Screen is 2400 x 1080. Discrete (0,0) → native (W-1, 0).
        val (x, y) = ScreenCoords.screenToNative(0f, 0f, nativeW, nativeH, ScreenCoords.ROTATION_90)
        assertEquals((nativeW - 1).toFloat(), x)
        assertEquals(0f, y)
    }

    @Test
    fun rotation270_mapsTopLeftScreenToNativeBottomLeftBand() {
        val (x, y) = ScreenCoords.screenToNative(0f, 0f, nativeW, nativeH, ScreenCoords.ROTATION_270)
        assertEquals(0f, x)
        assertEquals((nativeH - 1).toFloat(), y)
    }

    @Test
    fun landscape90_and_270_areDifferent() {
        val screenX = 100f
        val screenY = 50f
        val a = ScreenCoords.screenToNative(screenX, screenY, nativeW, nativeH, ScreenCoords.ROTATION_90)
        val b = ScreenCoords.screenToNative(screenX, screenY, nativeW, nativeH, ScreenCoords.ROTATION_270)
        assertFalse(abs(a.first - b.first) < 1f && abs(a.second - b.second) < 1f)
        // R90: (W-1-y, x) = (1029, 100)
        assertEquals((nativeW - 1) - screenY, a.first)
        assertEquals(screenX, a.second)
        // R270: (y, H-1-x) = (50, 2299)
        assertEquals(screenY, b.first)
        assertEquals((nativeH - 1) - screenX, b.second)
    }

    @Test
    fun rotation180_invertsBothAxes() {
        val (x, y) = ScreenCoords.screenToNative(10f, 20f, nativeW, nativeH, ScreenCoords.ROTATION_180)
        assertEquals((nativeW - 1) - 10f, x)
        assertEquals((nativeH - 1) - 20f, y)
    }

    @Test
    fun roundTrip_allRotations_cornersAndCenter() {
        val screenSizes = mapOf(
            ScreenCoords.ROTATION_0 to (nativeW to nativeH),
            ScreenCoords.ROTATION_90 to (nativeH to nativeW),
            ScreenCoords.ROTATION_180 to (nativeW to nativeH),
            ScreenCoords.ROTATION_270 to (nativeH to nativeW)
        )
        for ((rot, size) in screenSizes) {
            val (sw, sh) = size
            val points = listOf(
                0f to 0f,
                (sw - 1).toFloat() to 0f,
                0f to (sh - 1).toFloat(),
                (sw - 1).toFloat() to (sh - 1).toFloat(),
                (sw - 1) / 2f to (sh - 1) / 2f,
                123.4f to 56.7f
            )
            for ((sx, sy) in points) {
                val (nx, ny) = ScreenCoords.screenToNative(sx, sy, nativeW, nativeH, rot)
                val (rx, ry) = ScreenCoords.nativeToScreen(nx, ny, nativeW, nativeH, rot)
                assertTrue("rot=$rot screen=($sx,$sy) got=($rx,$ry)", abs(rx - sx) < 0.01f)
                assertTrue("rot=$rot screen=($sx,$sy) got=($rx,$ry)", abs(ry - sy) < 0.01f)

                val (nx2, ny2) = ScreenCoords.screenToNativeFromScreen(sx, sy, sw, sh, rot)
                assertEquals(nx, nx2)
                assertEquals(ny, ny2)
                val (sx2, sy2) = ScreenCoords.nativeToScreenFromScreen(nx, ny, sw, sh, rot)
                assertTrue(abs(sx2 - sx) < 0.01f)
                assertTrue(abs(sy2 - sy) < 0.01f)
            }
        }
    }

    @Test
    fun inputReader_rotation90_exampleFromAospDocs() {
        // 3x4 natural display: raw (0, 2) becomes screen (2, 2) at ROTATION_90.
        val (sx, sy) = ScreenCoords.nativeToScreen(0f, 2f, 3, 4, ScreenCoords.ROTATION_90)
        assertEquals(2f, sx)
        assertEquals(2f, sy)
        val (nx, ny) = ScreenCoords.screenToNative(2f, 2f, 3, 4, ScreenCoords.ROTATION_90)
        assertEquals(0f, nx)
        assertEquals(2f, ny)
    }
}
