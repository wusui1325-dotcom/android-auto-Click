package com.wusui.androidautoclick

import android.os.Handler
import android.os.Looper
import com.wusui.androidautoclick.service.ClickAccessibilityService

/**
 * 簡易序列錄製與回放。
 */
object SequenceRecorder {

    data class Step(
        val type: String, // "click" | "wait" | "swipe"
        val x: Float = 0f,
        val y: Float = 0f,
        val x2: Float = 0f,
        val y2: Float = 0f,
        val delayMs: Long = 0
    )

    val steps = mutableListOf<Step>()
    val saved = mutableMapOf<String, List<Step>>()
    var isRecording = false
        private set
    var replayLoops: Int = 1 // 0 = 無限

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

    fun addSwipe(x1: Float, y1: Float, x2: Float, y2: Float, delayMs: Long = 300) {
        if (!isRecording) return
        steps.add(Step("swipe", x1, y1, x2, y2, delayMs))
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

    fun clearSaved() {
        saved.clear()
    }

    fun duplicateCurrent(name: String) {
        if (steps.isEmpty()) return
        saved[name] = steps.toList()
    }

    fun importText(text: String) {
        steps.clear()
        text.lines().forEach { line ->
            val p = line.trim().split(" ")
            when (p.getOrNull(0)) {
                "click" -> {
                    val x = p.getOrNull(1)?.toFloatOrNull() ?: return@forEach
                    val y = p.getOrNull(2)?.toFloatOrNull() ?: return@forEach
                    val d = p.getOrNull(3)?.toLongOrNull() ?: 200
                    steps.add(Step("click", x, y, delayMs = d))
                }
                "wait" -> {
                    val d = p.getOrNull(1)?.toLongOrNull() ?: return@forEach
                    steps.add(Step("wait", delayMs = d))
                }
                "swipe" -> {
                    val x = p.getOrNull(1)?.toFloatOrNull() ?: return@forEach
                    val y = p.getOrNull(2)?.toFloatOrNull() ?: return@forEach
                    val x2 = p.getOrNull(3)?.toFloatOrNull() ?: return@forEach
                    val y2 = p.getOrNull(4)?.toFloatOrNull() ?: return@forEach
                    val d = p.getOrNull(5)?.toLongOrNull() ?: 300
                    steps.add(Step("swipe", x, y, x2, y2, d))
                }
            }
        }
    }

    private var currentLoop = 0

    fun replay() {
        if (isReplaying || steps.isEmpty()) return
        val service = ClickAccessibilityService.instance ?: return
        isReplaying = true
        replayIndex = 0
        currentLoop = 0
        playNext(service)
    }

    private fun playNext(service: ClickAccessibilityService) {
        if (!isReplaying) return
        if (replayIndex >= steps.size) {
            currentLoop++
            if (replayLoops > 0 && currentLoop >= replayLoops) {
                isReplaying = false
                return
            }
            replayIndex = 0
        }
        val step = steps[replayIndex++]
        when (step.type) {
            "click" -> service.performClick(step.x, step.y, ClickController.durationMs)
            "swipe" -> service.performSwipe(step.x, step.y, step.x2, step.y2, step.delayMs)
            "wait" -> {}
        }
        handler.postDelayed({ playNext(service) }, step.delayMs.coerceAtLeast(50))
    }

    fun stopReplay() {
        isReplaying = false
        handler.removeCallbacksAndMessages(null)
    }
}
