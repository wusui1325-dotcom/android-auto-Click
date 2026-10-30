package com.wusui.androidautoclick

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wusui.androidautoclick.service.ClickAccessibilityService
import com.wusui.androidautoclick.service.FloatingOverlayService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SettingsTheme {
                AppShell()
            }
        }
        updateSystemBars()
    }

    override fun onResume() {
        super.onResume()
        updateSystemBars()
    }

    private fun updateSystemBars() {
        val color = if (SettingsColors.isDark) android.graphics.Color.BLACK
        else android.graphics.Color.parseColor("#F2F2F7")
        window.statusBarColor = color
        window.navigationBarColor = color
    }
}

internal object SettingsColors {
    var isDark by mutableStateOf(true)

    val bg: Color get() = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val text: Color get() = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F)
    val secondary: Color get() = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)
    val divider: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFD1D1D6)
    val selected: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val rowActive: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val card: Color get() = if (isDark) Color(0xFF111111) else Color.White
    val cardBorder: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFD1D1D6)
    val arrow: Color = Color(0xFF636366)
    val circleBtn: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val circleBtnPressed: Color get() = if (isDark) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)
    val trackOff: Color get() = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val trackOn: Color get() = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
    val trackBorderOff: Color get() = if (isDark) Color(0xFF8E8E93) else Color(0xFFAEAEB2)
    val trackBorderOn: Color get() = if (isDark) Color(0xFFD1D1D6) else Color(0xFF8E8E93)
    val knob: Color get() = Color.White
}

@Composable
fun SettingsTheme(content: @Composable () -> Unit) {
    val dark = SettingsColors.isDark
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            onBackground = Color(0xFFF5F5F7),
            onSurface = Color(0xFFF5F5F7)
        ) else lightColorScheme(
            background = Color(0xFFF2F2F7),
            surface = Color(0xFFF2F2F7),
            onBackground = Color(0xFF1D1D1F),
            onSurface = Color(0xFF1D1D1F)
        ),
        content = content
    )
}

@Composable
fun AppShell() {
    val config = LocalConfiguration.current
    val isWide = config.screenWidthDp >= 900
    var selected by remember { mutableStateOf<String?>(if (isWide) "permissions" else null) }
    val context = LocalContext.current
    var accessibilityOn by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var overlayOn by remember { mutableStateOf(PermissionHelper.canDrawOverlay(context)) }
    var ready by remember { mutableStateOf(false) }

    var clickMode by remember { mutableStateOf("單點") }
    var interval by remember { mutableIntStateOf(200) }
    var count by remember { mutableStateOf("無限") }
    var duration by remember { mutableIntStateOf(50) }
    var randomOffset by remember { mutableStateOf(false) }
    var randomInterval by remember { mutableStateOf(false) }
    var antiDetect by remember { mutableStateOf(false) }
    var floatButton by remember { mutableStateOf(FloatingOverlayService.isRunning) }

    fun syncFromController() {
        clickMode = ClickController.mode
        interval = ClickController.intervalMs.toInt().coerceAtLeast(1)
        count = if (ClickController.count < 0) "無限" else ClickController.count.toString()
        duration = ClickController.durationMs.toInt().coerceAtLeast(1)
        randomOffset = ClickController.randomOffset
        randomInterval = ClickController.randomInterval
        antiDetect = ClickController.antiDetect
    }

    fun persistBasics(
        mode: String = clickMode,
        iv: Int = interval,
        ct: String = count,
        dur: Int = duration,
        ro: Boolean = randomOffset,
        ri: Boolean = randomInterval,
        ad: Boolean = antiDetect
    ) {
        ClickController.mode = mode
        ClickController.intervalMs = iv.toLong()
        ClickController.count = if (ct == "無限") -1 else ct.toIntOrNull() ?: -1
        ClickController.durationMs = dur.toLong()
        ClickController.randomOffset = ro
        ClickController.randomInterval = ri
        ClickController.antiDetect = ad
        SettingsStore.save()
    }

    LaunchedEffect(Unit) {
        ClickController.attach(context)
        SettingsStore.init(context)
        syncFromController()
        ready = true
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            (context as? android.app.Activity)?.requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
        (context as? android.app.Activity)?.let { act ->
            val color = if (SettingsColors.isDark) android.graphics.Color.BLACK
            else android.graphics.Color.parseColor("#F2F2F7")
            act.window.statusBarColor = color
            act.window.navigationBarColor = color
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessibilityOn = PermissionHelper.isAccessibilityEnabled(context)
                overlayOn = PermissionHelper.canDrawOverlay(context)
                floatButton = FloatingOverlayService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(SettingsColors.bg))
        return
    }

    val detail: @Composable (Modifier) -> Unit = { mod ->
        DetailPane(
            selected = selected ?: "permissions",
            accessibilityOn = accessibilityOn,
            overlayOn = overlayOn,
            clickMode = clickMode,
            onClickMode = { v ->
                clickMode = v
                persistBasics(mode = v)
            },
            interval = interval,
            onInterval = { v ->
                interval = v
                persistBasics(iv = v)
            },
            count = count,
            onCount = { v ->
                count = v
                persistBasics(ct = v)
            },
            duration = duration,
            onDuration = { v ->
                duration = v
                persistBasics(dur = v)
            },
            randomOffset = randomOffset,
            onRandomOffset = { v ->
                randomOffset = v
                persistBasics(ro = v)
            },
            randomInterval = randomInterval,
            onRandomInterval = { v ->
                randomInterval = v
                persistBasics(ri = v)
            },
            antiDetect = antiDetect,
            onAntiDetect = { v ->
                antiDetect = v
                persistBasics(ad = v)
            },
            floatButton = floatButton,
            onFloatButton = { enabled ->
                floatButton = enabled
                if (enabled && overlayOn) {
                    context.startService(
                        Intent(context, FloatingOverlayService::class.java)
                            .setAction(FloatingOverlayService.ACTION_SHOW)
                    )
                } else {
                    context.startService(
                        Intent(context, FloatingOverlayService::class.java)
                            .setAction(FloatingOverlayService.ACTION_HIDE)
                    )
                    floatButton = false
                }
            },
            onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
            onOpenOverlay = { PermissionHelper.openOverlaySettings(context) },
            onReloadUi = { syncFromController() },
            modifier = mod
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsColors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (isWide) {
            Row(Modifier.fillMaxSize()) {
                CategoryList(
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(0.30f)
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(SettingsColors.divider)
                )
                detail(Modifier.fillMaxSize())
            }
        } else {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(150)
                        ) { it } + fadeIn(androidx.compose.animation.core.tween(150)) togetherWith
                            slideOutHorizontally(
                                animationSpec = androidx.compose.animation.core.tween(150)
                            ) { -it / 3 } + fadeOut(androidx.compose.animation.core.tween(100))
                    } else {
                        slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(150)
                        ) { -it / 3 } + fadeIn(androidx.compose.animation.core.tween(150)) togetherWith
                            slideOutHorizontally(
                                animationSpec = androidx.compose.animation.core.tween(150)
                            ) { it } + fadeOut(androidx.compose.animation.core.tween(100))
                    }
                },
                label = "nav"
            ) { page ->
                if (page == null) {
                    CategoryList(
                        selected = null,
                        onSelect = { selected = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        DetailTopBar(title = titleFor(page), onBack = { selected = null })
                        detail(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

private fun titleFor(id: String) = when (id) {
    "permissions" -> "權限"
    "click" -> "點擊"
    "sequence" -> "序列"
    "overlay" -> "懸浮窗"
    "about" -> "關於"
    else -> id
}

@Composable
fun CategoryList(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(SettingsColors.bg).verticalScroll(rememberScrollState())) {
        Text(
            text = "AutoClick",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                color = SettingsColors.text
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )
        SettingsSection(label = "控制") {
            SettingsRow("權限", selected == "permissions") { onSelect("permissions") }
            SettingsRow("點擊", selected == "click") { onSelect("click") }
            SettingsRow("序列", selected == "sequence") { onSelect("sequence") }
            SettingsRow("懸浮窗", selected == "overlay") { onSelect("overlay") }
        }
        Spacer(Modifier.height(8.dp))
        SettingsSection(label = "其他") {
            SettingsRow("關於", selected == "about") { onSelect("about") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DetailPane(
    selected: String,
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    clickMode: String,
    onClickMode: (String) -> Unit,
    interval: Int,
    onInterval: (Int) -> Unit,
    count: String,
    onCount: (String) -> Unit,
    duration: Int,
    onDuration: (Int) -> Unit,
    randomOffset: Boolean,
    onRandomOffset: (Boolean) -> Unit,
    randomInterval: Boolean,
    onRandomInterval: (Boolean) -> Unit,
    antiDetect: Boolean,
    onAntiDetect: (Boolean) -> Unit,
    floatButton: Boolean,
    onFloatButton: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onReloadUi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(SettingsColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        when (selected) {
            "permissions" -> PermissionsDetail(accessibilityOn, overlayOn, onOpenAccessibility, onOpenOverlay)
            "click" -> ClickDetail(
                clickMode, onClickMode,
                interval, onInterval,
                count, onCount,
                duration, onDuration,
                randomOffset, onRandomOffset,
                randomInterval, onRandomInterval,
                antiDetect, onAntiDetect,
                onReloadUi
            )
            "sequence" -> SequenceDetail()
            "overlay" -> OverlayDetail(overlayOn, onOpenOverlay, floatButton, onFloatButton)
            "about" -> AboutDetail(onReloadUi)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun PermissionsDetail(
    accessibilityOn: Boolean,
    overlayOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    Spacer(Modifier.height(12.dp))
    DetailTitle("權限")
    Spacer(Modifier.height(16.dp))
    SettingsSection(label = "必要") {
        SettingsStatusRow("無障礙服務", if (accessibilityOn) "已開啟" else "未開啟", onOpenAccessibility)
        SettingsStatusRow("懸浮窗", if (overlayOn) "已開啟" else "未開啟", onOpenOverlay)
    }
    Spacer(Modifier.height(12.dp))
    Caption(
        if (accessibilityOn && overlayOn) "權限已就緒，可開始使用。"
        else "無障礙服務用於在其他應用中執行點擊，必須在系統設定中手動開啟。懸浮窗用於浮動控制面板。"
    )
}

@Composable
fun ClickDetail(
    clickMode: String, onClickMode: (String) -> Unit,
    interval: Int, onInterval: (Int) -> Unit,
    count: String, onCount: (String) -> Unit,
    duration: Int, onDuration: (Int) -> Unit,
    randomOffset: Boolean, onRandomOffset: (Boolean) -> Unit,
    randomInterval: Boolean, onRandomInterval: (Boolean) -> Unit,
    antiDetect: Boolean, onAntiDetect: (Boolean) -> Unit,
    onReloadUi: () -> Unit
) {
    val context = LocalContext.current
    // read observable state so start/stop refreshes UI
    val running = ClickController.isRunning
    val paused = ClickController.isPaused
    val total = ClickController.totalClicks
    val err = ClickController.lastError
    val remain = ClickController.remainingDisplay
    val logs = ClickController.log.toList()
    val pts = ClickController.points.toList()

    Spacer(Modifier.height(12.dp))
    DetailTitle("點擊")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "快速") {
        SettingsStatusRow(
            if (running) "停止" else "開始",
            when {
                running -> "${ClickController.elapsedSec()}s · 剩餘 $remain"
                err.isNotEmpty() -> err
                else -> ""
            }
        ) {
            ClickController.toggle()
        }
        SettingsStatusRow("單次測試", "") {
            val p = ClickController.points.firstOrNull() ?: return@SettingsStatusRow
            if (!ClickAccessibilityService.isEnabled()) {
                ClickController.clearError()
                return@SettingsStatusRow
            }
            ClickAccessibilityService.instance?.performClick(p.first, p.second, ClickController.durationMs)
        }
        SettingsStatusRow("暫停／繼續", if (paused) "已暫停" else if (running) "執行中" else "") {
            if (!running) return@SettingsStatusRow
            if (paused) ClickController.resume() else ClickController.pause()
        }
        SettingsStatusRow("總點擊", total.toString()) { ClickController.resetTotal() }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "基本") {
        CycleRow("模式", clickMode, listOf("單點", "多點", "滑動")) { onClickMode(it) }
        CycleRow("間隔", "$interval ms", listOf(50, 100, 200, 300, 500, 1000).map { "$it ms" }) { v ->
            onInterval(v.removeSuffix(" ms").toInt())
        }
        CycleRow("時長", "$duration ms", listOf(30, 50, 80, 120, 200, 400).map { "$it ms" }) { v ->
            onDuration(v.removeSuffix(" ms").toInt())
        }
        CycleRow("次數", count, listOf("無限", "10", "50", "100", "500")) { onCount(it) }
        CycleRow(
            "跳過每 N",
            if (ClickController.skipEvery == 0) "關閉" else ClickController.skipEvery.toString(),
            listOf("關閉", "3", "5", "10")
        ) { v ->
            ClickController.skipEvery = if (v == "關閉") 0 else v.toInt()
            SettingsStore.save()
        }
        CycleRow(
            "暖機次數",
            ClickController.warmupClicks.toString(),
            listOf(0, 3, 5, 10).map { it.toString() }
        ) { v -> ClickController.warmupClicks = v.toInt() }
        CycleRow(
            "爆發次數",
            ClickController.burstCount.toString(),
            listOf(1, 2, 3, 5).map { it.toString() }
        ) { v -> ClickController.burstCount = v.toInt() }
        CycleRow(
            "爆發後等待",
            "${ClickController.burstPause} ms",
            listOf(0, 100, 300, 500).map { "$it ms" }
        ) { v -> ClickController.burstPause = v.removeSuffix(" ms").toLong() }
        CycleRow(
            "每 N 暫停",
            if (ClickController.pauseEvery == 0) "關閉" else ClickController.pauseEvery.toString(),
            listOf("關閉", "10", "50", "100")
        ) { v -> ClickController.pauseEvery = if (v == "關閉") 0 else v.toInt() }
        CycleRow(
            "總點擊上限",
            if (ClickController.stopAtTotal == 0) "無限" else ClickController.stopAtTotal.toString(),
            listOf("無限", "100", "500", "1000")
        ) { v -> ClickController.stopAtTotal = if (v == "無限") 0 else v.toInt() }
        CycleRow(
            "最長時間",
            if (ClickController.maxMinutes == 0) "無限" else "${ClickController.maxMinutes} 分",
            listOf("無限", "1 分", "5 分", "10 分", "30 分")
        ) { v ->
            ClickController.maxMinutes = if (v == "無限") 0 else v.removeSuffix(" 分").toIntOrNull() ?: 0
            SettingsStore.save()
        }
        CycleRow(
            "開始延遲",
            "${ClickController.startDelayMs} ms",
            listOf(0, 500, 1000, 2000).map { "$it ms" }
        ) { v ->
            ClickController.startDelayMs = v.removeSuffix(" ms").toLong()
            SettingsStore.save()
        }
        SettingsStatusRow(
            "狀態",
            when {
                running -> "執行中 ${ClickController.elapsedSec()}s"
                err.isNotEmpty() -> err
                !ClickAccessibilityService.isEnabled() -> "需開啟無障礙"
                else -> "停止"
            }
        ) {
            if (ClickAccessibilityService.isEnabled()) ClickController.toggle()
            else {
                // force error path for UI feedback
                ClickController.start()
            }
        }
        SettingsStatusRow("測試滑動", "點1→點2") {
            if (!ClickAccessibilityService.isEnabled()) return@SettingsStatusRow
            val a = ClickController.points.getOrNull(0) ?: return@SettingsStatusRow
            val b = ClickController.points.getOrNull(1) ?: (a.first to (a.second - 400f))
            ClickAccessibilityService.instance?.performSwipe(a.first, a.second, b.first, b.second, 300)
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "快速預設") {
        SettingsStatusRow("慢速簽到", "500ms × 10") {
            ClickController.intervalMs = 500
            ClickController.count = 10
            SettingsStore.save()
            onReloadUi()
        }
        SettingsStatusRow("快速連點", "50ms ∞") {
            ClickController.intervalMs = 50
            ClickController.count = -1
            SettingsStore.save()
            onReloadUi()
        }
        SettingsStatusRow("中速任務", "200ms × 50") {
            ClickController.intervalMs = 200
            ClickController.count = 50
            SettingsStore.save()
            onReloadUi()
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "座標") {
        SettingsStatusRow("點 1", pointsText(0, pts)) {}
        SettingsStatusRow("點 2", pointsText(1, pts)) {}
        SettingsStatusRow("點 3", pointsText(2, pts)) {}
        CycleRow("縮放", String.format("%.1f", ClickController.scale), listOf("0.8", "0.9", "1.0", "1.1", "1.2")) { v ->
            ClickController.scale = v.toFloat()
        }
        CycleRow(
            "全域偏移 X",
            ClickController.offsetX.toInt().toString(),
            listOf(-50, -20, 0, 20, 50).map { it.toString() }
        ) { v -> ClickController.offsetX = v.toFloat() }
        CycleRow(
            "全域偏移 Y",
            ClickController.offsetY.toInt().toString(),
            listOf(-50, -20, 0, 20, 50).map { it.toString() }
        ) { v -> ClickController.offsetY = v.toFloat() }
        CycleRow(
            "最後點 X",
            pts.lastOrNull()?.first?.toInt()?.toString() ?: "540",
            listOf(200, 400, 540, 700, 900).map { it.toString() }
        ) { v ->
            val y = ClickController.points.lastOrNull()?.second ?: 960f
            if (ClickController.points.isNotEmpty()) {
                ClickController.points[ClickController.points.lastIndex] = v.toFloat() to y
            }
        }
        CycleRow(
            "最後點 Y",
            pts.lastOrNull()?.second?.toInt()?.toString() ?: "960",
            listOf(400, 700, 960, 1200, 1600).map { it.toString() }
        ) { v ->
            val x = ClickController.points.lastOrNull()?.first ?: 540f
            if (ClickController.points.isNotEmpty()) {
                ClickController.points[ClickController.points.lastIndex] = x to v.toFloat()
            }
        }
        SettingsStatusRow("新增點", "${pts.size} 點") { ClickController.addPoint(540f, 960f) }
        SettingsStatusRow("刪除最後點", "至少保留 1") { ClickController.removeLastPoint() }
        SettingsStatusRow("複製最後點", "") { ClickController.duplicateLastPoint() }
        SettingsStatusRow("清空點", "重置為預設") { ClickController.clearPoints() }
        SettingsStatusRow("儲存為常用", "") { ClickController.saveFavorite() }
        SettingsStatusRow("清空常用", "") { ClickController.clearFavorites() }
        SettingsStatusRow("從剪貼簿貼上點", "x,y") {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return@SettingsStatusRow
            val parts = text.split(",", " ", "\n").filter { it.isNotBlank() }
            val x = parts.getOrNull(0)?.toFloatOrNull() ?: return@SettingsStatusRow
            val y = parts.getOrNull(1)?.toFloatOrNull() ?: return@SettingsStatusRow
            ClickController.addPoint(x, y)
        }
        SettingsStatusRow("複製座標", "") {
            val p = ClickController.points.lastOrNull() ?: return@SettingsStatusRow
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("point", "${p.first.toInt()},${p.second.toInt()}"))
        }
        if (ClickController.favoritePoints.isNotEmpty()) {
            ClickController.favoritePoints.forEachIndexed { i, p ->
                SettingsStatusRow("常用 ${i + 1}", "${p.first.toInt()},${p.second.toInt()}") {
                    ClickController.loadFavorite(i)
                }
            }
        }
        SettingsStatusRow("微調 X +10", "") { ClickController.nudgeLastPoint(10f, 0f) }
        SettingsStatusRow("微調 X -10", "") { ClickController.nudgeLastPoint(-10f, 0f) }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "進階") {
        ToggleRow("垂直鏡像", ClickController.mirrorY) { ClickController.mirrorY = it }
        ToggleRow("水平鏡像", ClickController.mirrorX) { ClickController.mirrorX = it }
        ToggleRow("開始提示", ClickController.showToast) { ClickController.showToast = it }
        ToggleRow("結束提示音", ClickController.endBeep) { ClickController.endBeep = it }
        ToggleRow("漸慢", ClickController.decelerate) { ClickController.decelerate = it }
        ToggleRow("漸快", ClickController.invertInterval) { ClickController.invertInterval = it }
        ToggleRow("慢啟動", ClickController.slowStart) { ClickController.slowStart = it }
        ToggleRow("長按式", ClickController.longPress) { ClickController.longPress = it }
        ToggleRow("雙擊", ClickController.doubleTap) { ClickController.doubleTap = it }
        CycleRow(
            "記錄每 N",
            ClickController.logEvery.toString(),
            listOf(1, 5, 10, 50).map { it.toString() }
        ) { v -> ClickController.logEvery = v.toInt() }
        ToggleRow("記錄點擊", ClickController.logClicks) { ClickController.logClicks = it }
        ToggleRow("除錯記錄", ClickController.debugLog) { ClickController.debugLog = it }
        ToggleRow("安全模式", ClickController.safeMode) { ClickController.safeMode = it }
        ToggleRow("失敗即停", ClickController.stopOnError) { ClickController.stopOnError = it }
        ToggleRow("擬人化", ClickController.humanize) { ClickController.humanize = it }
        ToggleRow("隨機偏移", randomOffset) { onRandomOffset(it) }
        ToggleRow("限制螢幕內", ClickController.clampToScreen) { ClickController.clampToScreen = it }
        ToggleRow("隨機選點", ClickController.randomPoint) { ClickController.randomPoint = it }
        ToggleRow("來回", ClickController.pingPong) { ClickController.pingPong = it }
        ToggleRow("多點反向", ClickController.reversePoints) { ClickController.reversePoints = it }
        ToggleRow("只點奇數", ClickController.onlyOdd) { ClickController.onlyOdd = it }
        ToggleRow("只點偶數", ClickController.onlyEven) { ClickController.onlyEven = it }
        ToggleRow("間隔微抖", ClickController.jitterInterval) { ClickController.jitterInterval = it }
        ToggleRow("隨機間隔", randomInterval) { onRandomInterval(it) }
        ToggleRow("防偵測延遲", antiDetect) { onAntiDetect(it) }
        ToggleRow("開始震動", ClickController.vibrateOnStart) { ClickController.vibrateOnStart = it }
        ToggleRow("保持螢幕", ClickController.keepScreenOn) {
            ClickController.keepScreenOn = it
            if (it) {
                (context as? android.app.Activity)?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                (context as? android.app.Activity)?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        CycleRow(
            "CPS 上限",
            ClickController.maxCps.toString(),
            listOf(5, 10, 20, 30, 50).map { it.toString() }
        ) { v ->
            ClickController.maxCps = v.toInt()
            SettingsStore.save()
        }
        ToggleRow("開始清空記錄", ClickController.clearLogOnStart) { ClickController.clearLogOnStart = it }
        ToggleRow("點擊音效", ClickController.soundOnClick) { ClickController.soundOnClick = it }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "記錄") {
        if (logs.isEmpty()) {
            SettingsStatusRow("尚無記錄", "") {}
        } else {
            logs.take(5).forEach { line -> SettingsStatusRow(line, "") {} }
            SettingsStatusRow("清空記錄", "") { ClickController.log.clear() }
        }
    }

    Spacer(Modifier.height(12.dp))
    Caption(
        when (clickMode) {
            "滑動" -> "滑動模式：依序在相鄰座標間滑動（至少 2 點）。手勢完成後才發下一發。"
            "多點" -> "多點模式：依序點擊所有座標。手勢串行，不會 burst 狂丟。"
            else -> "單點模式：重複點擊第 1 點。間隔／次數／時長會寫入引擎並持久化。"
        }
    )
}

@Composable
fun SequenceDetail() {
    val context = LocalContext.current
    val recording = SequenceRecorder.isRecording
    val replaying = SequenceRecorder.isReplaying
    val steps = SequenceRecorder.steps.toList()
    val saved = SequenceRecorder.saved.toMap()

    Spacer(Modifier.height(12.dp))
    DetailTitle("序列")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "錄製") {
        SettingsStatusRow("開始錄製", if (recording) "錄製中 · 真實間隔" else "") { SequenceRecorder.start() }
        SettingsStatusRow("加入測試點", "") {
            SequenceRecorder.addClick(540f, 960f) // 真實時間戳間隔
        }
        SettingsStatusRow("加入等待 500ms", "") { SequenceRecorder.addWait(500) }
        SettingsStatusRow("加入測試滑動", "") {
            SequenceRecorder.addSwipe(400f, 1200f, 400f, 400f)
        }
        SettingsStatusRow("停止並儲存", "${SequenceRecorder.steps.size} 步") {
            SequenceRecorder.stop()
            SequenceRecorder.save("未命名 ${SequenceRecorder.saved.size + 1}")
        }
        SettingsStatusRow("回放目前", if (replaying) "回放中" else "") {
            if (SequenceRecorder.isReplaying) SequenceRecorder.stopReplay() else SequenceRecorder.replay()
        }
        CycleRow(
            "回放次數",
            if (SequenceRecorder.replayLoops == 0) "無限" else SequenceRecorder.replayLoops.toString(),
            listOf("1", "3", "5", "無限")
        ) { v ->
            SequenceRecorder.replayLoops = if (v == "無限") 0 else v.toInt()
            SettingsStore.save()
        }
        SettingsStatusRow("匯出文字", "") {
            val text = SequenceRecorder.exportText()
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("sequence", text))
        }
        SettingsStatusRow("從剪貼簿匯入", "") {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return@SettingsStatusRow
            SequenceRecorder.importText(text)
        }
        SettingsStatusRow("保留前 10 步", "") { SequenceRecorder.trimTo(10) }
        SettingsStatusRow("複製最後一步", "") {
            if (SequenceRecorder.steps.isNotEmpty()) SequenceRecorder.duplicateStep(SequenceRecorder.steps.lastIndex)
        }
        SettingsStatusRow("打亂步驟", "") { SequenceRecorder.shuffle() }
        SettingsStatusRow("反轉步驟", "") { SequenceRecorder.reverse() }
        SettingsStatusRow("上移最後一步", "") {
            if (SequenceRecorder.steps.isNotEmpty()) SequenceRecorder.moveUp(SequenceRecorder.steps.lastIndex)
        }
        SettingsStatusRow("清空目前", "") { SequenceRecorder.clear() }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "目前步驟") {
        if (steps.isEmpty()) {
            SettingsStatusRow("尚無步驟", "點開始錄製") {}
        } else {
            steps.forEachIndexed { i, step ->
                val label = when (step.type) {
                    "click" -> "點擊 ${step.x.toInt()},${step.y.toInt()}"
                    "swipe" -> "滑動 ${step.x.toInt()},${step.y.toInt()}→${step.x2.toInt()},${step.y2.toInt()}"
                    "wait" -> "等待 ${step.delayMs}ms"
                    else -> step.type
                }
                SettingsStatusRow("$i $label", "刪") { SequenceRecorder.removeAt(i) }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "已儲存") {
        if (saved.isEmpty()) {
            SettingsStatusRow("尚無序列", "") {}
        } else {
            saved.keys.forEach { name ->
                SettingsStatusRow(name, "${saved[name]?.size ?: 0} 步") { SequenceRecorder.load(name) }
            }
        }
        SettingsStatusRow("複製目前為新序列", "") {
            SequenceRecorder.duplicateCurrent("副本 ${SequenceRecorder.saved.size + 1}")
        }
        SettingsStatusRow("清空已儲存", "") { SequenceRecorder.clearSaved() }
    }

    Spacer(Modifier.height(12.dp))
    Caption("目前 ${steps.size} 步。步間間隔取真實時間戳。錄製僅能收到無障礙 VIEW_CLICKED（系統／原生控件）；遊戲自繪／OpenGL 畫面通常錄不到，請改用手點「加入測試點／滑動」或匯入文字。點擊步驟可刪除。重開 App 會還原目前步驟與已儲存序列。")
}

@Composable
fun OverlayDetail(
    overlayOn: Boolean,
    onOpenOverlay: () -> Unit,
    floatButton: Boolean,
    onFloatButton: (Boolean) -> Unit
) {
    Spacer(Modifier.height(12.dp))
    DetailTitle("懸浮窗")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "顯示") {
        SettingsStatusRow("狀態", if (overlayOn) "權限已開" else "未授權", onOpenOverlay)
        ToggleRow("浮動按鈕", floatButton) { onFloatButton(it) }
        ToggleRow("顯示標記點", ClickController.showMarkers) {
            ClickController.showMarkers = it
            SettingsStore.save()
        }
        ToggleRow("懸浮窗顯示座標", ClickController.showCoordsOnOverlay) {
            ClickController.showCoordsOnOverlay = it
        }
    }

    Spacer(Modifier.height(12.dp))
    Caption("單擊浮動鈕：開始／停止點擊。長按：展開面板（新增點／刪除最後／顯示標記／收起）。面板可拖曳。")
}

@Composable
fun AboutDetail(onReloadUi: () -> Unit) {
    val context = LocalContext.current
    Spacer(Modifier.height(12.dp))
    DetailTitle("關於")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "應用") {
        SettingsStatusRow("版本", "1.6.0") {}
        SettingsStatusRow("建置", "P0 修復＋Settings 風") {}
        SettingsStatusRow("立即停止", "") {
            ClickController.stop()
            SequenceRecorder.stopReplay()
        }
        SettingsStatusRow("清除錯誤", "") { ClickController.clearError() }
        SettingsStatusRow("重設預設", "") {
            ClickController.resetDefaults()
            SettingsStore.save()
            onReloadUi()
        }
        SettingsStatusRow("匯出設定", "") {
            val text = SettingsStore.exportText()
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("settings", text))
        }
        SettingsStatusRow("匯入設定", "") {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return@SettingsStatusRow
            SettingsStore.importText(text)
            onReloadUi()
        }
        SettingsStatusRow("切換主題", if (SettingsColors.isDark) "深色" else "淺色") {
            SettingsColors.isDark = !SettingsColors.isDark
            SettingsStore.save()
            (context as? android.app.Activity)?.let { act ->
                val color = if (SettingsColors.isDark) android.graphics.Color.BLACK
                else android.graphics.Color.parseColor("#F2F2F7")
                act.window.statusBarColor = color
                act.window.navigationBarColor = color
            }
        }
        SettingsStatusRow("授權", "MIT") {}
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "功能") {
        SettingsStatusRow("連點引擎", "串行手勢") {}
        SettingsStatusRow("滑動模式", "已實作") {}
        SettingsStatusRow("懸浮窗", "可拖曳標記") {}
        SettingsStatusRow("序列回放", "已實作") {}
        SettingsStatusRow("設定持久化", "已實作") {}
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "原則") {
        SettingsStatusRow("無廣告", "是") {}
        SettingsStatusRow("無追蹤", "是") {}
        SettingsStatusRow("開源", "是") {}
        SettingsStatusRow("簽名金鑰", "見 Drive secrets") {}
    }

    Spacer(Modifier.height(12.dp))
    Caption("極簡、免廣告、高對比的自動點擊工具。簽名金鑰不進公開倉，見 Drive「android-auto-Click-secrets」。")
}

private fun pointsText(index: Int, pts: List<Pair<Float, Float>>): String {
    val p = pts.getOrNull(index) ?: return "未設定"
    return "${p.first.toInt()}, ${p.second.toInt()}"
}

@Composable
fun DetailTitle(text: String) {
    Text(
        text,
        style = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight(650),
            color = SettingsColors.text
        )
    )
}

@Composable
fun Caption(text: String) {
    Text(
        text,
        style = TextStyle(
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = SettingsColors.secondary
        ),
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun DetailTopBar(title: String, onBack: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (pressed) SettingsColors.circleBtnPressed else SettingsColors.circleBtn)
                .clickable(interactionSource = interaction, indication = null) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", color = SettingsColors.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            title,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight(650),
                color = SettingsColors.text
            ),
            modifier = Modifier.weight(1f).padding(end = 36.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                color = SettingsColors.secondary
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SettingsColors.card)
                .border(1.dp, SettingsColors.cardBorder, RoundedCornerShape(14.dp))
        ) {
            content()
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun SettingsRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    pressed -> SettingsColors.rowActive
                    selected -> SettingsColors.selected
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight(560),
                letterSpacing = (-0.01).em,
                color = SettingsColors.text
            ),
            modifier = Modifier.weight(1f)
        )
        Text("›", color = SettingsColors.arrow, fontSize = 18.sp)
    }
}

@Composable
fun SettingsStatusRow(label: String, value: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) SettingsColors.rowActive else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight(560),
                letterSpacing = (-0.01).em,
                color = SettingsColors.text
            ),
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(value, style = TextStyle(fontSize = 16.sp, color = SettingsColors.secondary))
            Spacer(Modifier.width(6.dp))
        }
        Text("›", color = SettingsColors.arrow, fontSize = 18.sp)
    }
}

@Composable
fun CycleRow(label: String, current: String, options: List<String>, onChange: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) SettingsColors.rowActive else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) {
                val idx = options.indexOf(current).let { if (it < 0) 0 else it }
                onChange(options[(idx + 1) % options.size])
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight(560),
                letterSpacing = (-0.01).em,
                color = SettingsColors.text
            ),
            modifier = Modifier.weight(1f)
        )
        Text(current, style = TextStyle(fontSize = 16.sp, color = SettingsColors.secondary))
        Spacer(Modifier.width(6.dp))
        Text("›", color = SettingsColors.arrow, fontSize = 18.sp)
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) SettingsColors.rowActive else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight(560),
                letterSpacing = (-0.01).em,
                color = SettingsColors.text
            ),
            modifier = Modifier.weight(1f)
        )
        // iOS-ish gray switch (not green)
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) SettingsColors.trackOn else SettingsColors.trackOff)
                .border(
                    1.dp,
                    if (checked) SettingsColors.trackBorderOn else SettingsColors.trackBorderOff,
                    RoundedCornerShape(999.dp)
                ),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(SettingsColors.knob)
            )
        }
    }
}
