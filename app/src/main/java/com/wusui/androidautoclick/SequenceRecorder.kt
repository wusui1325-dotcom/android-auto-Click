package com.wusui.androidautoclick

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wusui.androidautoclick.service.ClickAccessibilityService

object SequenceRecorder {

    data class Step(
        val type: String, // "click" | "wait" | "swipe"
        val x: Float = 0f,
        val y: Float = 0f,
        val x2: Float = 0f,
        val y2: Float = 0f,
        val delayMs: Long = 0
    )

    val steps = mutableStateListOf<Step>()
    val saved = mutableStateMapOf<String, List<Step>>()
    var isRecording by mutableStateOf(false)
        private set
    var isReplaying by mutableStateOf(false)
        private set
    var replayLoops by mutableIntStateOf(1) // 0 = 無限

    private val handler = Handler(Looper.getMainLooper())
    private var replayIndex = 0
    private var currentLoop = 0
    private var generation = 0
    /** elapsedRealtime of last recorded event; used for real inter-step delay */
    private var lastStampMs = 0L

    fun start() {
        isRecording = true
        steps.clear()
        lastStampMs = SystemClock.elapsedRealtime()
    }

    fun stop() {
        isRecording = false
        SettingsStore.saveSequences()
    }

    /**
     * @param delayMs if null, use wall-clock gap since previous event (or 0 for first).
     */
    fun addClick(x: Float, y: Float, delayMs: Long? = null) {
        if (!isRecording) return
        steps.add(Step("click", x = x, y = y, delayMs = resolveDelay(delayMs)))
        SettingsStore.saveSequences()
    }

    fun addWait(ms: Long) {
        if (!isRecording) return
        // wait steps use explicit duration; also advance stamp so next click gap is after this wait
        steps.add(Step("wait", delayMs = ms.coerceAtLeast(0)))
        lastStampMs = SystemClock.elapsedRealtime()
        SettingsStore.saveSequences()
    }

    fun addSwipe(x1: Float, y1: Float, x2: Float, y2: Float, delayMs: Long? = null) {
        if (!isRecording) return
        steps.add(Step("swipe", x1, y1, x2, y2, resolveDelay(delayMs)))
        SettingsStore.saveSequences()
    }

    private fun resolveDelay(explicit: Long?): Long {
        val now = SystemClock.elapsedRealtime()
        val d = when {
            explicit != null -> explicit.coerceAtLeast(0)
            steps.isEmpty() -> 0L
            else -> (now - lastStampMs).coerceAtLeast(0).coerceAtMost(60_000)
        }
        lastStampMs = now
        return d
    }

    fun clear() {
        steps.clear()
        isRecording = false
        stopReplay()
        SettingsStore.saveSequences()
    }

    fun clearSaved() {
        saved.clear()
        SettingsStore.saveSequences()
    }

    fun reverse() {
        steps.reverse()
        SettingsStore.saveSequences()
    }

    fun shuffle() {
        steps.shuffle()
        SettingsStore.saveSequences()
    }

    fun save(name: String) {
        if (steps.isEmpty()) return
        saved[name] = steps.toList()
        SettingsStore.saveSequences()
    }

    fun load(name: String) {
        saved[name]?.let {
            steps.clear()
            steps.addAll(it)
            SettingsStore.saveSequences()
        }
    }

    fun exportText(): String = steps.joinToString("\n") { s ->
        when (s.type) {
            "click" -> "click ${s.x.toInt()} ${s.y.toInt()} ${s.delayMs}"
            "wait" -> "wait ${s.delayMs}"
            "swipe" -> "swipe ${s.x.toInt()} ${s.y.toInt()} ${s.x2.toInt()} ${s.y2.toInt()} ${s.delayMs}"
            else -> s.type
        }
    }

    fun removeAt(index: Int) {
        if (index in steps.indices) {
            steps.removeAt(index)
            SettingsStore.saveSequences()
        }
    }

    fun moveUp(index: Int) {
        if (index > 0 && index < steps.size) {
            val tmp = steps[index]
            steps[index] = steps[index - 1]
            steps[index - 1] = tmp
            SettingsStore.saveSequences()
        }
    }

    fun duplicateStep(index: Int) {
        if (index in steps.indices) {
            steps.add(index + 1, steps[index])
            SettingsStore.saveSequences()
        }
    }

    fun trimTo(max: Int) {
        while (steps.size > max) steps.removeAt(steps.lastIndex)
        SettingsStore.saveSequences()
    }

    fun duplicateCurrent(name: String) {
        if (steps.isEmpty()) return
        saved[name] = steps.toList()
        SettingsStore.saveSequences()
    }

    fun importText(text: String, persist: Boolean = true) {
        steps.clear()
        text.lines().forEach { line ->
            parseStepLine(line)?.let { steps.add(it) }
        }
        if (persist) SettingsStore.saveSequences()
    }

    /** Restore saved map from SettingsStore encoding (blocks separated by ====). */
    fun loadSavedFromEncoded(encoded: String) {
        saved.clear()
        if (encoded.isBlank()) return
        encoded.split("\n====\n").forEach { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return@forEach
            val name = lines.first()
            val list = lines.drop(1).mapNotNull { parseStepLine(it) }
            if (list.isNotEmpty()) saved[name] = list
        }
    }

    private fun parseStepLine(line: String): Step? {
        val p = line.trim().split(" ")
        return when (p.getOrNull(0)) {
            "click" -> {
                val x = p.getOrNull(1)?.toFloatOrNull() ?: return null
                val y = p.getOrNull(2)?.toFloatOrNull() ?: return null
                val d = p.getOrNull(3)?.toLongOrNull() ?: 200
                Step("click", x, y, delayMs = d)
            }
            "wait" -> {
                val d = p.getOrNull(1)?.toLongOrNull() ?: return null
                Step("wait", delayMs = d)
            }
            "swipe" -> {
                val x = p.getOrNull(1)?.toFloatOrNull() ?: return null
                val y = p.getOrNull(2)?.toFloatOrNull() ?: return null
                val x2 = p.getOrNull(3)?.toFloatOrNull() ?: return null
                val y2 = p.getOrNull(4)?.toFloatOrNull() ?: return null
                val d = p.getOrNull(5)?.toLongOrNull() ?: 300
                Step("swipe", x, y, x2, y2, d)
            }
            else -> null
        }
    }

    fun replay() {
        if (isReplaying || steps.isEmpty()) return
        val service = ClickAccessibilityService.instance ?: return
        isReplaying = true
        replayIndex = 0
        currentLoop = 0
        val gen = ++generation
        playNext(service, gen)
    }

    private fun playNext(service: ClickAccessibilityService, gen: Int) {
        if (!isReplaying || gen != generation) return
        if (replayIndex >= steps.size) {
            currentLoop++
            if (replayLoops > 0 && currentLoop >= replayLoops) {
                isReplaying = false
                return
            }
            replayIndex = 0
        }
        val step = steps.getOrNull(replayIndex) ?: run {
            isReplaying = false
            return
        }
        replayIndex++
        // delayMs = gap BEFORE this step (from previous); wait type = wait duration
        val gap = when (step.type) {
            "wait" -> step.delayMs.coerceAtLeast(0)
            else -> step.delayMs.coerceAtLeast(0)
        }
        val runStep = Runnable {
            if (!isReplaying || gen != generation) return@Runnable
            when (step.type) {
                "click" -> {
                    val ok = service.performClick(step.x, step.y, ClickController.durationMs) {
                        if (isReplaying && gen == generation) playNext(service, gen)
                    }
                    if (!ok && isReplaying && gen == generation) playNext(service, gen)
                }
                "swipe" -> {
                    val swipeDur = ClickController.durationMs.coerceAtLeast(80)
                    val ok = service.performSwipe(
                        step.x, step.y, step.x2, step.y2, swipeDur
                    ) {
                        if (isReplaying && gen == generation) playNext(service, gen)
                    }
                    if (!ok && isReplaying && gen == generation) playNext(service, gen)
                }
                "wait" -> playNext(service, gen)
                else -> playNext(service, gen)
            }
        }
        if (gap > 0) handler.postDelayed(runStep, gap) else runStep.run()
    }

    fun stopReplay() {
        generation++
        isReplaying = false
        handler.removeCallbacksAndMessages(null)
    }
}
