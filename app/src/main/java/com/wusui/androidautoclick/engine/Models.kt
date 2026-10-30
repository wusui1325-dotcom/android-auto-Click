package com.wusui.androidautoclick.engine

import java.util.UUID

enum class SessionType {
    SINGLE, MULTI, RECORD;

    fun label(): String = when (this) {
        SINGLE -> "單點"
        MULTI -> "多點"
        RECORD -> "錄製"
    }
}

enum class NodeKind { CLICK, DRAG_START, DRAG_END }

/**
 * Ordered touch / drag endpoint. [order] is 1-based display & cycle order.
 * For drag pairs, [groupId] links START+END; END is skipped in playback.
 */
data class TouchNode(
    val id: String = UUID.randomUUID().toString(),
    val order: Int,
    val x: Float,
    val y: Float,
    /** Gap after finishing this node's action(s), before the next order. */
    val intervalToNextMs: Long = 200L,
    /** How many taps at this point each cycle (CLICK only). */
    val clickCount: Int = 1,
    val kind: NodeKind = NodeKind.CLICK,
    val groupId: String? = null
)

data class GlobalParams(
    /** Number of full cycles; -1 = infinite. */
    val cycleCount: Int = -1,
    /** Stop after this many ms from start; 0 = no time limit. */
    val stopAfterMs: Long = 0L,
    /** Small random offset on each tap (anti-detect). */
    val jitter: Boolean = false,
    val tapDurationMs: Long = 50L,
    val swipeDurationMs: Long = 280L
)

data class Scheme(
    val name: String,
    val type: SessionType,
    val nodes: List<TouchNode>,
    val params: GlobalParams = GlobalParams()
)

data class EngineStatus(
    val running: Boolean = false,
    val recording: Boolean = false,
    val totalActions: Int = 0,
    val currentOrder: Int = 0,
    val cycleIndex: Int = 0,
    val lastError: String = "",
    val message: String = ""
) {
    val active: Boolean get() = running
}
