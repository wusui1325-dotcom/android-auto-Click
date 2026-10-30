package com.wusui.androidautoclick.engine

enum class ClickMode {
    SINGLE, MULTI, SWIPE, SEQUENCE;

    fun label(): String = when (this) {
        SINGLE -> "單點"
        MULTI -> "多點"
        SWIPE -> "滑動"
        SEQUENCE -> "序列"
    }

    companion object {
        fun fromLabel(s: String): ClickMode = entries.find { it.label() == s }
            ?: entries.find { it.name.equals(s, ignoreCase = true) }
            ?: SINGLE
    }
}

data class ClickPoint(val x: Float, val y: Float)

data class SequenceStep(
    val type: StepType,
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    /** Gap before this step (ms), or wait duration when type=WAIT. */
    val delayMs: Long = 0L
) {
    enum class StepType { CLICK, WAIT, SWIPE }

    fun summary(): String = when (type) {
        StepType.CLICK -> "點擊 ${x.toInt()},${y.toInt()}  ·  前距 ${delayMs}ms"
        StepType.WAIT -> "等待 ${delayMs}ms"
        StepType.SWIPE -> "滑動 ${x.toInt()},${y.toInt()}→${x2.toInt()},${y2.toInt()}  ·  前距 ${delayMs}ms"
    }
}

data class EngineConfig(
    val intervalMs: Long = 200L,
    val durationMs: Long = 50L,
    val count: Int = -1, // -1 = infinite
    val mode: ClickMode = ClickMode.SINGLE,
    val randomOffset: Boolean = false,
    val randomInterval: Boolean = false,
    val startDelayMs: Long = 0L,
    val sequenceLoops: Int = 1, // 0 = infinite
    val showMarkers: Boolean = true,
    /** Hide / untouchable markers while running so finger can't cancel gestures. */
    val lockMarkersWhileRunning: Boolean = true,
    val hapticFeedback: Boolean = true,
    val darkTheme: Boolean = true
)

data class EngineStatus(
    val running: Boolean = false,
    val totalActions: Int = 0,
    val remaining: String = "∞",
    val currentIndex: Int = 0,
    val lastError: String = "",
    val recording: Boolean = false,
    val replaying: Boolean = false
) {
    val active: Boolean get() = running || replaying
}
