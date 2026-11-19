package com.wusui.androidautoclick.root

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wusui.androidautoclick.overlay.ScreenMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class RootStatus {
    /** No su binary visible to the app. */
    NOT_DETECTED,
    /** su exists, but the user has not granted a session yet. */
    NEED_GRANT,
    /** Live privileged session is ready to inject. */
    AUTHORIZED,
    /** The user (or su) refused authorization. */
    DENIED
}

/**
 * Optional root click backend. OFF by default (see AppStore).
 *
 * Fast path: a kept-alive su process running the bundled uinput helper,
 * accepting TAP/SWIPE without starting a new JVM per click.
 *
 * Last-resort fallback: a persistent su shell running `input swipe` /
 * `input tap`. That still starts an app_process JVM per gesture and is
 * too slow for tight auto-click intervals — only used when uinput cannot
 * be set up. The caveat stays in this comment, not in a user-facing banner.
 *
 * su is used only to inject the user's own marked taps/swipes. This is not
 * a general root shell UI, and no su binary is bundled.
 */
object RootClickBackend {
    private const val TAG = "RootClick"
    private const val HELPER_ASSET_PREFIX = "root_uinput"
    private const val HELPER_REMOTE = "/data/local/tmp/com.wusui.androidautoclick_uinput"
    private const val GRANT_TIMEOUT_MS = 60_000L
    private const val QUICK_RESTART_MS = 4_000L
    private const val IO_TIMEOUT_PAD_MS = 3_000L

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "root-click").apply { isDaemon = true }
    }

    private val _status = MutableStateFlow(detectStatus())
    val status: StateFlow<RootStatus> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val sessionLock = Any()
    private var helper: LineSession? = null
    private var fallbackSu: LineSession? = null
    private var useFallback = false
    private var lastW = 0
    private var lastH = 0
    private var detectedSu: String? = null
    private val generation = AtomicInteger(0)

    fun isReady(): Boolean = synchronized(sessionLock) { isReadyLocked() }

    /** Re-scan su without prompting. Does not drop a live authorized session. */
    fun refreshDetection() {
        if (isReady()) {
            _status.value = RootStatus.AUTHORIZED
            return
        }
        if (_status.value == RootStatus.AUTHORIZED) {
            _status.value = if (findSu() != null) RootStatus.NEED_GRANT else RootStatus.NOT_DETECTED
            return
        }
        if (_status.value == RootStatus.DENIED) {
            if (findSu() == null) _status.value = RootStatus.NOT_DETECTED
            return
        }
        _status.value = detectStatus()
    }

    /**
     * Request su once (may show the superuser prompt) and start or reuse the
     * injector session. Safe to call from the main thread; work runs off it.
     */
    fun requestAccess(context: Context, onDone: ((RootStatus) -> Unit)? = null) {
        val app = context.applicationContext
        _busy.value = true
        io.execute {
            val result = try {
                ensureSession(app, timeoutMs = GRANT_TIMEOUT_MS)
            } catch (_: Exception) {
                RootStatus.DENIED
            }
            _status.value = result
            _busy.value = false
            if (onDone != null) main.post { onDone(result) }
        }
    }

    /**
     * For playback start on a background thread: reuse a live session, or try
     * a short restart (already-granted su usually returns immediately). Does
     * not wait the full grant timeout — the Settings toggle is what prompts.
     */
    fun ensureReadyBlocking(context: Context): RootStatus {
        if (isReady()) return RootStatus.AUTHORIZED
        return try {
            val result = ensureSession(context.applicationContext, timeoutMs = QUICK_RESTART_MS)
            _status.value = result
            result
        } catch (_: Exception) {
            val s = if (findSu() == null) RootStatus.NOT_DETECTED else RootStatus.NEED_GRANT
            _status.value = s
            s
        }
    }

    fun click(x: Float, y: Float, durationMs: Long, onDone: (Boolean) -> Unit) {
        submit(RootProtocol.tap(x, y, durationMs), durationMs, onDone)
    }

    fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
        onDone: (Boolean) -> Unit
    ) {
        submit(RootProtocol.swipe(x1, y1, x2, y2, durationMs), durationMs, onDone)
    }

    fun notifyScreenSize(width: Int, height: Int) {
        if (width == lastW && height == lastH) return
        lastW = width
        lastH = height
        if (!isReady() || useFallback) return
        io.execute {
            synchronized(sessionLock) {
                val h = helper
                if (h != null && h.isAlive()) {
                    h.sendAndWait(RootProtocol.config(width, height), IO_TIMEOUT_PAD_MS)
                }
            }
        }
    }

    fun resetQueue() {
        generation.incrementAndGet()
    }

    private fun submit(command: String, durationMs: Long, onDone: (Boolean) -> Unit) {
        val gen = generation.get()
        io.execute {
            if (gen != generation.get()) {
                main.post { onDone(false) }
                return@execute
            }
            val timeout = durationMs.coerceAtLeast(1L) + IO_TIMEOUT_PAD_MS
            val ok = synchronized(sessionLock) {
                performLocked(command, timeout)
            }
            if (gen != generation.get()) {
                main.post { onDone(false) }
                return@execute
            }
            if (!ok && _status.value == RootStatus.AUTHORIZED && !isReady()) {
                _status.value = if (findSu() != null) RootStatus.NEED_GRANT else RootStatus.NOT_DETECTED
            }
            main.post { onDone(ok) }
        }
    }

    private fun performLocked(command: String, timeoutMs: Long): Boolean {
        if (useFallback) {
            val su = fallbackSu?.takeIf { it.isAlive() } ?: return false
            return runInputFallback(su, command, timeoutMs)
        }
        val h = helper?.takeIf { it.isAlive() } ?: return false
        val reply = h.sendAndWait(command, timeoutMs) ?: return false
        return RootProtocol.isOk(reply)
    }

    /**
     * input tap / input swipe last-resort path. `input` starts a JVM each
     * time, so this is only used when the uinput helper cannot be set up.
     */
    private fun runInputFallback(su: LineSession, command: String, timeoutMs: Long): Boolean {
        val parts = command.split(' ')
        val shellCmd = when (parts[0]) {
            "TAP" -> {
                if (parts.size < 4) return false
                // input tap has no press duration; swipe-to-same-point is the timed equivalent.
                "input swipe ${parts[1]} ${parts[2]} ${parts[1]} ${parts[2]} ${parts[3]}"
            }
            "SWIPE" -> {
                if (parts.size < 6) return false
                "input swipe ${parts[1]} ${parts[2]} ${parts[3]} ${parts[4]} ${parts[5]}"
            }
            else -> return false
        }
        val reply = su.sendAndWait(shellCmd, timeoutMs) ?: return false
        return reply != "ERR" && !reply.startsWith("ERR ")
    }

    private fun ensureSession(context: Context, timeoutMs: Long): RootStatus {
        synchronized(sessionLock) {
            if (isReadyLocked()) return RootStatus.AUTHORIZED
            closeSessionsLocked()
            val suPath = findSu() ?: "su"

            val auth = openSu(suPath, timeoutMs) ?: return if (findSu() == null) {
                RootStatus.NOT_DETECTED
            } else {
                RootStatus.DENIED
            }

            val (w, h) = try {
                ScreenMetrics.realSize(context)
            } catch (_: Exception) {
                1080 to 1920
            }
            lastW = w
            lastH = h

            val helperFile = extractHelper(context)
            if (helperFile != null && startUinputHelper(auth, helperFile, w, h)) {
                useFallback = false
                auth.closeQuietly()
                return RootStatus.AUTHORIZED
            }

            // Keep this su shell for the slow input fallback.
            if (prepareFallback(auth)) {
                helper = null
                fallbackSu = auth
                useFallback = true
                return RootStatus.AUTHORIZED
            }
            auth.closeQuietly()
            return RootStatus.DENIED
        }
    }

    private fun isReadyLocked(): Boolean {
        return (helper != null && helper!!.isAlive()) ||
            (useFallback && fallbackSu != null && fallbackSu!!.isAlive())
    }

    private fun startUinputHelper(auth: LineSession, helperFile: File, w: Int, h: Int): Boolean {
        val local = helperFile.absolutePath
        auth.sendAndWait("cp '$local' '$HELPER_REMOTE'", 8_000) ?: return false
        auth.sendAndWait("chmod 700 '$HELPER_REMOTE'", 8_000) ?: return false
        val suPath = detectedSu ?: "su"
        val commandCandidates = listOf(HELPER_REMOTE, local)
        for (cmd in commandCandidates) {
            val proc = try {
                ProcessBuilder(suPath, "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
            } catch (e: Exception) {
                Log.w(TAG, "helper start failed for $cmd", e)
                continue
            }
            val session = LineSession(proc, markerMode = false)
            val ping = session.waitForProtocol(RootProtocol.PING, 8_000)
            if (!RootProtocol.isOk(ping)) {
                session.closeQuietly()
                continue
            }
            val cfg = session.waitForProtocol(RootProtocol.config(w, h), 8_000)
            if (!RootProtocol.isOk(cfg)) {
                session.closeQuietly()
                continue
            }
            helper = session
            useFallback = false
            return true
        }
        return false
    }

    private fun prepareFallback(auth: LineSession): Boolean {
        val probe = auth.sendAndWait("command -v input || which input", 8_000) ?: return false
        return probe != "TIMEOUT" &&
            !probe.contains("not found", ignoreCase = true) &&
            !probe.startsWith("ERR ")
    }

    private fun openSu(suPath: String, timeoutMs: Long): LineSession? {
        val proc = try {
            ProcessBuilder(suPath)
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return null
        }
        val session = LineSession(proc, markerMode = true)
        val idReply = session.sendAndWait("id", timeoutMs)
        if (idReply == null || (!idReply.contains("uid=0") && !idReply.contains("uid=0("))) {
            session.closeQuietly()
            return null
        }
        return session
    }

    private fun extractHelper(context: Context): File? {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val names = (abis + listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")).distinct()
        for (abi in names) {
            val asset = "$HELPER_ASSET_PREFIX/$abi/root_uinput"
            try {
                context.assets.open(asset).use { input ->
                    val out = File(context.codeCacheDir, "root_uinput")
                    out.outputStream().use { input.copyTo(it) }
                    out.setReadable(true, false)
                    out.setExecutable(true, false)
                    if (out.length() > 0) return out
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun closeSessionsLocked() {
        helper?.closeQuietly()
        helper = null
        fallbackSu?.closeQuietly()
        fallbackSu = null
        useFallback = false
    }

    private fun detectStatus(): RootStatus =
        if (findSu() != null) RootStatus.NEED_GRANT else RootStatus.NOT_DETECTED

    private fun findSu(): String? {
        val cached = detectedSu
        if (cached != null) {
            if (cached == "su") return cached
            val f = File(cached)
            if (f.exists() || f.canExecute()) return cached
        }
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/debug_ramdisk/su",
            "/system/bin/.ext/su",
            "/data/adb/magisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su"
        )
        for (p in paths) {
            val f = File(p)
            if (f.exists() && f.canExecute()) {
                detectedSu = p
                return p
            }
        }
        try {
            val proc = ProcessBuilder("which", "su").redirectErrorStream(true).start()
            if (proc.waitFor(1, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                val line = proc.inputStream.bufferedReader().readLine()?.trim()
                if (!line.isNullOrEmpty()) {
                    detectedSu = line
                    return line
                }
            } else {
                proc.destroy()
            }
        } catch (_: Exception) {
        }
        detectedSu = null
        return null
    }

    /**
     * @param markerMode when true (interactive su shell), wrap each command
     * with unique begin/end markers so we can detect completion even if the
     * command prints nothing (needed for `input` fallback).
     */
    private class LineSession(
        private val process: Process,
        private val markerMode: Boolean
    ) {
        private val writer = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
        private val lines = LinkedBlockingQueue<String>()
        private val seq = AtomicInteger(0)
        private val poison = "<EOF>"

        init {
            val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
            Thread({
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        lines.put(line)
                    }
                } catch (_: Exception) {
                }
                try { lines.put(poison) } catch (_: Exception) {}
            }, "root-click-read").apply { isDaemon = true; start() }
        }

        fun isAlive(): Boolean = process.isAlive

        fun sendAndWait(command: String, timeoutMs: Long): String? {
            if (!process.isAlive) return null
            return try {
                if (markerMode) sendMarked(command, timeoutMs) else sendDirect(command, timeoutMs)
            } catch (_: Exception) {
                null
            }
        }

        /** Direct protocol: skip su banners until OK/ERR. */
        fun waitForProtocol(command: String, timeoutMs: Long): String? {
            if (!process.isAlive) return null
            return try {
                writeLine(command)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val remain = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                    val line = readLineTimeout(remain) ?: return null
                    if (RootProtocol.isOk(line) || line.startsWith("ERR")) return line
                }
                null
            } catch (_: Exception) {
                null
            }
        }

        private fun sendDirect(command: String, timeoutMs: Long): String? {
            writeLine(command)
            return readLineTimeout(timeoutMs)
        }

        private fun sendMarked(command: String, timeoutMs: Long): String? {
            val id = seq.incrementAndGet()
            val begin = "BEGIN_$id"
            val end = "END_$id"
            writeLine("echo $begin; $command; echo $end:\$?")
            val body = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + timeoutMs
            var started = false
            while (System.currentTimeMillis() < deadline) {
                val remain = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
                val line = readLineTimeout(remain) ?: break
                when {
                    line == begin -> started = true
                    line.startsWith("$end:") -> {
                        val code = line.substringAfter(":").trim()
                        val text = body.joinToString("\n")
                        return if (code == "0") text.ifEmpty { "OK" } else "ERR ${text}".trim()
                    }
                    started -> body += line
                }
            }
            return null
        }

        private fun writeLine(command: String) {
            writer.write(command)
            writer.write("\n")
            writer.flush()
        }

        private fun readLineTimeout(timeoutMs: Long): String? {
            val line = lines.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
            if (line == poison) {
                try { lines.put(poison) } catch (_: Exception) {}
                return null
            }
            return line
        }

        fun closeQuietly() {
            try {
                if (markerMode) writeLine("exit") else writeLine(RootProtocol.QUIT)
            } catch (_: Exception) {
            }
            try {
                if (!process.waitFor(400, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            } catch (_: Exception) {
                try { process.destroyForcibly() } catch (_: Exception) {}
            }
        }
    }
}
