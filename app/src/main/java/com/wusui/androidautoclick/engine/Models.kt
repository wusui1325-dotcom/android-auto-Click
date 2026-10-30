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

/** Named interval / count presets for one-tap setup. */
data class ClickPreset(
    val id: String,
    val label: String,
    val subtitle: String,
    val intervalMs: Long,
    val durationMs: Long = 50L,
    val count: Int = -1
)

object ClickPresets {
    val all: List<ClickPreset> = listOf(
        ClickPreset("turbo", "極速連點", "50ms · 無限", 50, 30, -1),
        ClickPreset("fast", "快速連點", "100ms · 無限", 100, 40, -1),
        ClickPreset("normal", "一般連點", "200ms · 無限", 200, 50, -1),
        ClickPreset("steady", "穩定連點", "500ms · 無限", 500, 50, -1),
        ClickPreset("slow", "慢速點擊", "1000ms · 無限", 1000, 60, -1),
        ClickPreset("burst50", "爆發 50 次", "80ms · 50 次", 80, 40, 50),
        ClickPreset("burst100", "爆發 100 次", "100ms · 100 次", 100, 40, 100),
        ClickPreset("ten", "點 10 次", "200ms · 10 次", 200, 50, 10)
    )
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
    val lockMarkersWhileRunning: Boolean = true,
    val hapticFeedback: Boolean = true,
    val compactOverlay: Boolean = true,
    val activeSequenceName: String = "",
    val darkTheme: Boolean = true
)

data class RunStats(
    val lifetimeActions: Long = 0L,
    val lifetimeSessions: Long = 0L,
    val lastSessionActions: Int = 0,
    val lastSessionMs: Long = 0L,
    val sessionActions: Int = 0,
    val sessionStartedAt: Long = 0L
) {
    fun lastSessionLabel(): String {
        if (lastSessionMs <= 0 && lastSessionActions <= 0) return "尚無"
        val sec = lastSessionMs / 1000.0
        return "${lastSessionActions} 次 · ${"%.1f".format(sec)}s"
    }
}

data class EngineStatus(
    val running: Boolean = false,
    val totalActions: Int = 0,
    val remaining: String = "∞",
    val currentIndex: Int = 0,
    val lastError: String = "",
    val recording: Boolean = false,
    val replaying: Boolean = false,
    val message: String = ""
) {
    val active: Boolean get() = running || replaying
}
