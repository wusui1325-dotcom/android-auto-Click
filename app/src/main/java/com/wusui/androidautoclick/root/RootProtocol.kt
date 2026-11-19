package com.wusui.androidautoclick.root

import kotlin.math.roundToInt

/**
 * Line protocol for the persistent root injector.
 *
 * CONFIG nativeW nativeH [rotation]
 *   nativeW/H are the display's natural (ROTATION_0) pixel size — the
 *   helper's stable ABS range. rotation is Surface.getRotation() (0/1/2/3).
 *   A two-argument CONFIG is accepted as rotation 0 (backward-safe).
 *
 * TAP / SWIPE coordinates are in that same natural axis space (Kotlin
 * converts from screen pixels before sending).
 */
object RootProtocol {
    data class ConfigCmd(val nativeWidth: Int, val nativeHeight: Int, val rotation: Int)

    fun config(nativeWidth: Int, nativeHeight: Int, rotation: Int = 0): String =
        "CONFIG ${nativeWidth.coerceAtLeast(1)} ${nativeHeight.coerceAtLeast(1)} ${normalizeRotation(rotation)}"

    fun tap(x: Float, y: Float, durationMs: Long): String =
        "TAP ${x.roundToInt()} ${y.roundToInt()} ${durationMs.coerceIn(1L, 60_000L)}"

    fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long
    ): String =
        "SWIPE ${x1.roundToInt()} ${y1.roundToInt()} ${x2.roundToInt()} ${y2.roundToInt()} ${durationMs.coerceIn(1L, 60_000L)}"

    const val PING = "PING"
    const val QUIT = "QUIT"

    fun normalizeRotation(rotation: Int): Int {
        val r = rotation % 4
        return if (r < 0) r + 4 else r
    }

    /**
     * Parse a CONFIG line. Accepts "CONFIG w h" (rotation defaults to 0)
     * and "CONFIG w h rotation". Returns null if the line is not CONFIG
     * or the first two integers are missing.
     */
    fun parseConfig(line: String): ConfigCmd? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.isEmpty() || parts[0] != "CONFIG") return null
        if (parts.size < 3) return null
        val w = parts[1].toIntOrNull() ?: return null
        val h = parts[2].toIntOrNull() ?: return null
        val rot = if (parts.size >= 4) parts[3].toIntOrNull() ?: 0 else 0
        return ConfigCmd(w.coerceAtLeast(1), h.coerceAtLeast(1), normalizeRotation(rot))
    }

    fun isOk(reply: String?): Boolean {
        if (reply == null) return false
        val t = reply.trim()
        return t == "OK" || t.startsWith("OK ")
    }
}
