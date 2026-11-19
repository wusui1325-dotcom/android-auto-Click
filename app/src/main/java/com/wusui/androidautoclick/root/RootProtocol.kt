package com.wusui.androidautoclick.root

import kotlin.math.roundToInt

/** Line protocol for the persistent root uinput helper. */
object RootProtocol {
    fun config(width: Int, height: Int): String =
        "CONFIG ${width.coerceAtLeast(1)} ${height.coerceAtLeast(1)}"

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

    fun isOk(reply: String?): Boolean {
        if (reply == null) return false
        val t = reply.trim()
        return t == "OK" || t.startsWith("OK ")
    }
}
