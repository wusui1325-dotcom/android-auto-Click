package com.wusui.androidautoclick

/**
 * 簡易序列錄製器（佔位）。
 * 後續可從無障礙事件或手動標記收集點擊。
 */
object SequenceRecorder {

    data class Step(
        val type: String, // "click" | "wait" | "swipe"
        val x: Float = 0f,
        val y: Float = 0f,
        val delayMs: Long = 0
    )

    val steps = mutableListOf<Step>()
    var isRecording = false
        private set

    fun start() {
        isRecording = true
        steps.clear()
    }

    fun stop() {
        isRecording = false
    }

    fun addClick(x: Float, y: Float, delayMs: Long = 0) {
        if (!isRecording) return
        steps.add(Step("click", x, y, delayMs))
    }

    fun addWait(ms: Long) {
        if (!isRecording) return
        steps.add(Step("wait", delayMs = ms))
    }

    fun clear() {
        steps.clear()
        isRecording = false
    }

    fun replay(controller: ClickController = ClickController) {
        // 佔位：後續可依序執行 steps
    }
}
