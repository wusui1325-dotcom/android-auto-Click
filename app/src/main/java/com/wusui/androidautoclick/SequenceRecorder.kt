package com.wusui.androidautoclick

import android.os.Handler
import android.os.Looper
import com.wusui.androidautoclick.service.ClickAccessibilityService

/**
 * 簡易序列錄製與回放。
 */
object SequenceRecorder {

    data class Step(
        val type: String, // "click" | "wait"
        val x: Float = 0f,
        val y: Float = 0f,
        val delayMs: Long = 0
    )

    val steps = mutableListOf<Step>()
    val saved = mutableMapOf<String, List<Step>>()
    var isRecording = false
        private set
    var isReplaying = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var replayIndex = 0

    fun start() {
        isRecording = true
        steps.clear()
    }

    fun stop() {
        isRecording = false
    }

    fun addClick(x: Float, y: Float, delayMs: Long = 200) {
        if (!isRecording) return
        steps.add(Step("click", x, y, delayMs))
    }

    fun addWait(ms: Long) {
        if (!isRecording) return
        steps.add(Step("wait", delayMs = ms))
    }

    fun removeAt(index: Int) {
        if (index in steps.indices) steps.removeAt(index)
    }

    fun moveUp(index: Int) {
        if (index > 0 && index < steps.size) {
            val tmp = steps[index]
            steps[index] = steps[index - 1]
            steps[index - 1] = tmp
        }
    }

    fun save(name: String) {
        if (steps.isEmpty()) return
        saved[name] = steps.toList()
    }

    fun exportText(): String {
        return steps.joinToString("\n") { s ->
            when (s.type) {
                "click" -> "click ${s.x.toInt()} ${s.y.toInt()} ${s.delayMs}"
                "wait" -> "wait ${s.delayMs}"
                else -> s.type
            }
        }
    }

    fun replay() {
        if (isReplaying || steps.isEmpty()) return
        val service = ClickAccessibilityService.instance ?: return
        isReplaying = true
        replayIndex = 0
        playNext(service)
    }

    private fun playNext(service: ClickAccessibilityService) {
        if (!isReplaying || replayIndex >= steps.size) {
            isReplaying = false
            return
        }
        val step = steps[replayIndex++]
        when (step.type) {
            "click" -> service.performClick(step.x, step.y, ClickController.durationMs)
            "wait" -> {}
        }
        handler.postDelayed({ playNext(service) }, step.delayMs.coerceAtLeast(50))
    }

    fun stopReplay() {
        isReplaying = false
        handler.removeCallbacksAndMessages(null)
    }
}
