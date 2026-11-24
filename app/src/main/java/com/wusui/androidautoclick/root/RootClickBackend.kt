package com.wusui.androidautoclick.root

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wusui.androidautoclick.overlay.ScreenCoords
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
 * Fast path: a kept-alive su process running the bundled helper, which
 * prefers injecting into the real Type-B touchscreen on a high free slot
 * (a second finger next to the user's) and otherwise uses a long-lived
 * uinput device with a stable natural-orientation ABS range. TAP/SWIPE
 * stay on that process — no new JVM per click.
 *
 * Last-resort fallback: a persistent su shell running `input swipe` /
 * `input tap`. That starts an app_process JVM per gesture and is too
 * slow for tight auto-click intervals — only used when the helper cannot
 * be set up at all. A successful Root grant uses the helper, not `input`.
 * The caveat stays in this comment, not in a user-facing banner.
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
    private const val CONFIG_TIMEOUT_MS = 4_000L

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
    private var lastScreenW = 0
    private var lastScreenH = 0
    private var lastNativeW = 0
    private var lastNativeH = 0
    private var lastRotation = 0
    private var appContext: Context? = null
    private var detectedSu: String? = null
    private val generation = AtomicInteger(0)

    private data class DisplayState(
        val screenW: Int,
        val screenH: Int,
        val nativeW: Int,
        val nativeH: Int,
        val rotation: Int
    )

    private fun isErrReply(reply: String?): Boolean {
        if (reply == null) return true
        val t = reply.trim()
        return t == "ERR" || t.startsWith("ERR ")
    }

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
        appContext = app
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
        appContext = context.applicationContext
        if (isReady()) {
            io.execute { synchronized(sessionLock) { syncConfigLocked(readDisplayState()) } }
            return RootStatus.AUTHORIZED
        }
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
        submit(durationMs, onDone) { state, fallback ->
            if (fallback) {
                RootProtocol.tap(x, y, durationMs)
            } else {
                val (nx, ny) = ScreenCoords.screenToNative(x, y, state.nativeW, state.nativeH, state.rotation)
                RootProtocol.tap(nx, ny, durationMs)
            }
        }
    }

    fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
        onDone: (Boolean) -> Unit
    ) {
        submit(durationMs, onDone) { state, fallback ->
            if (fallback) {
                RootProtocol.swipe(x1, y1, x2, y2, durationMs)
            } else {
                val (n1x, n1y) = ScreenCoords.screenToNative(x1, y1, state.nativeW, state.nativeH, state.rotation)
                val (n2x, n2y) = ScreenCoords.screenToNative(x2, y2, state.nativeW, state.nativeH, state.rotation)
                RootProtocol.swipe(n1x, n1y, n2x, n2y, durationMs)
            }
        }
    }

    /**
     * Current screen size (getLocationOnScreen / dispatchGesture space) plus
     * [android.view.Display.getRotation]. Sends a cheap CONFIG (native size +
     * rotation) to the helper; the uinput device is not recreated on rotation.
     */
    fun notifyScreenSize(width: Int, height: Int, rotation: Int = lastRotation) {
        val state = displayStateFrom(width, height, rotation)
        if (state.nativeW == lastNativeW && state.nativeH == lastNativeH &&
            state.rotation == lastRotation &&
            state.screenW == lastScreenW && state.screenH == lastScreenH
        ) {
            return
        }
        // input fallback uses the current display; no CONFIG to send.
        if (!isReady() || useFallback) {
            rememberDisplay(state)
            return
        }
        io.execute {
            synchronized(sessionLock) { syncConfigLocked(state) }
        }
    }

    fun resetQueue() {
        generation.incrementAndGet()
    }

    private fun submit(
        durationMs: Long,
        onDone: (Boolean) -> Unit,
        encode: (DisplayState, Boolean) -> String
    ) {
        val gen = generation.get()
        io.execute {
            if (gen != generation.get()) {
                main.post { onDone(false) }
                return@execute
            }
            val timeout = durationMs.coerceAtLeast(1L) + IO_TIMEOUT_PAD_MS
            val ok = synchronized(sessionLock) {
                performLocked(timeout, encode)
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

    private fun performLocked(timeoutMs: Long, encode: (DisplayState, Boolean) -> String): Boolean {
        val state = readDisplayState()
        if (useFallback) {
            rememberDisplay(state)
            val su = fallbackSu?.takeIf { it.isAlive() } ?: return false
            return runInputFallback(su, encode(state, true), timeoutMs)
        }
        if (!syncConfigLocked(state)) return false
        val h = helper?.takeIf { it.isAlive() } ?: return false
        val reply = h.sendAndWait(encode(state, false), timeoutMs) ?: return false
        return RootProtocol.isOk(reply)
    }

    private fun displayStateFrom(screenW: Int, screenH: Int, rotation: Int): DisplayState {
        val (nw, nh) = ScreenCoords.nativeSize(screenW, screenH, rotation)
        return DisplayState(
            screenW = screenW.coerceAtLeast(1),
            screenH = screenH.coerceAtLeast(1),
            nativeW = nw,
            nativeH = nh,
            rotation = RootProtocol.normalizeRotation(rotation)
        )
    }

    private fun readDisplayState(): DisplayState {
        val ctx = appContext
        if (ctx != null) {
            try {
                val (sw, sh) = ScreenMetrics.realSize(ctx)
                return displayStateFrom(sw, sh, ScreenMetrics.rotation(ctx))
            } catch (_: Exception) {
            }
        }
        val sw = lastScreenW.coerceAtLeast(1)
        val sh = lastScreenH.coerceAtLeast(1)
        val rot = lastRotation
        return if (lastNativeW > 0 && lastNativeH > 0) {
            DisplayState(sw, sh, lastNativeW, lastNativeH, rot)
        } else {
            displayStateFrom(sw, sh, rot)
        }
    }

    private fun rememberDisplay(state: DisplayState) {
        lastScreenW = state.screenW
        lastScreenH = state.screenH
        lastNativeW = state.nativeW
        lastNativeH = state.nativeH
        lastRotation = state.rotation
    }

    /**
     * Push native size + rotation to the helper. Rotation-only updates are
     * cheap (no device rebuild). Recreate happens only if native size changes
     * or the fd dies, and that is the helper's decision.
     */
    private fun syncConfigLocked(state: DisplayState, timeoutMs: Long = CONFIG_TIMEOUT_MS): Boolean {
        if (useFallback || helper?.isAlive() != true) {
            rememberDisplay(state)
            return helper?.isAlive() == true || useFallback
        }
        if (state.nativeW == lastNativeW && state.nativeH == lastNativeH &&
            state.rotation == lastRotation
        ) {
            lastScreenW = state.screenW
            lastScreenH = state.screenH
            return true
        }
        val h = helper?.takeIf { it.isAlive() } ?: return false
        val cmd = RootProtocol.config(state.nativeW, state.nativeH, state.rotation)
        var ok = RootProtocol.isOk(h.sendAndWait(cmd, timeoutMs))
        if (!ok && h.isAlive()) {
            ok = RootProtocol.isOk(h.sendAndWait(cmd, timeoutMs))
        }
        if (ok) {
            rememberDisplay(state)
            return true
        }
        // Do not bump last* (so a later notify can retry).
        // Drop the session so playback stops visibly instead of
        // injecting against a stale ABS range.
        closeSessionsLocked()
        _status.value = if (findSu() != null) RootStatus.NEED_GRANT else RootStatus.NOT_DETECTED
        return false
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
        return !isErrReply(reply)
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

            appContext = context.applicationContext
            val state = try {
                val (w, h) = ScreenMetrics.realSize(context)
                displayStateFrom(w, h, ScreenMetrics.rotation(context))
            } catch (_: Exception) {
                displayStateFrom(1080, 1920, 0)
            }
            rememberDisplay(state)

            val helperFile = extractHelper(context)
            if (helperFile != null && startUinputHelper(auth, helperFile, state)) {
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

    private fun startUinputHelper(auth: LineSession, helperFile: File, state: DisplayState): Boolean {
        val local = helperFile.absolutePath
        val cpRes = auth.sendAndWait("cp '$local' '$HELPER_REMOTE.tmp' && mv -f '$HELPER_REMOTE.tmp' '$HELPER_REMOTE'", 8_000)
        val remoteReady = !isErrReply(cpRes) &&
            !isErrReply(auth.sendAndWait("chmod 700 '$HELPER_REMOTE'", 8_000))
        val suPath = detectedSu ?: "su"
        val commandCandidates = if (remoteReady) listOf(HELPER_REMOTE, local) else listOf(local)
        for (cmd in commandCandidates) {
            val proc = try {
                // Keep stderr separate from the OK/ERR line protocol.
                ProcessBuilder(suPath, "-c", cmd).start()
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
            val cfg = session.waitForProtocol(
                RootProtocol.config(state.nativeW, state.nativeH, state.rotation),
                CONFIG_TIMEOUT_MS
            )
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
            !isErrReply(probe)
    }

    private fun openSu(suPath: String, timeoutMs: Long): LineSession? {
        val proc = try {
            // Keep su warnings off the marked stdout protocol.
            ProcessBuilder(suPath).start()
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
                    val tmp = File(context.codeCacheDir, "root_uinput.tmp")
                    val out = File(context.codeCacheDir, "root_uinput")
                    tmp.outputStream().use { input.copyTo(it) }
                    tmp.setReadable(true, false)
                    tmp.setExecutable(true, false)
                    if (tmp.length() > 0) {
                        if (tmp.renameTo(out)) return out
                        // Fallback if rename fails on some filesystems
                        tmp.copyTo(out, overwrite = true)
                        tmp.delete()
                        out.setReadable(true, false)
                        out.setExecutable(true, false)
                        if (out.length() > 0) return out
                    } else {
                        tmp.delete()
                    }
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
            if (f.exists() && f.canExecute()) return cached
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
            // Drain stderr so the process cannot block, but never mix it into the protocol.
            Thread({
                try {
                    BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).use { r ->
                        var line = r.readLine()
                        while (line != null) {
                            if (line.isNotBlank()) Log.w(TAG, line)
                            line = r.readLine()
                        }
                    }
                } catch (_: Exception) {
                }
            }, "root-click-stderr").apply { isDaemon = true; start() }
        }

        fun isAlive(): Boolean = process.isAlive

        fun sendAndWait(command: String, timeoutMs: Long): String? {
            if (!process.isAlive) return null
            return try {
                if (markerMode) sendMarked(command, timeoutMs) else waitForProtocol(command, timeoutMs)
            } catch (_: Exception) {
                null
            }
        }

        /** Direct protocol: skip su banners until OK/ERR. Used for every helper command. */
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
