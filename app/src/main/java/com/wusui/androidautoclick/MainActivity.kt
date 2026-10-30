package com.wusui.androidautoclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import android.content.Intent
import com.wusui.androidautoclick.service.FloatingOverlayService
import com.wusui.androidautoclick.service.ClickAccessibilityService
import com.wusui.androidautoclick.ClickController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SettingsTheme {
                AppShell()
            }
        }
    }
}

private object SettingsColors {
    var isDark = true

    val bg: Color get() = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val text: Color get() = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F)
    val secondary: Color get() = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)
    val divider: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFD1D1D6)
    val selected: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val card: Color get() = if (isDark) Color(0xFF111111) else Color.White
    val cardBorder: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFD1D1D6)
    val arrow: Color = Color(0xFF636366)
    val circleBtn: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val circleBtnPressed: Color get() = if (isDark) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)
}

@Composable
fun SettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = SettingsColors.bg,
            surface = SettingsColors.bg,
            onBackground = SettingsColors.text,
            onSurface = SettingsColors.text
        ),
        content = content
    )
}

@Composable
fun AppShell() {
    val config = LocalConfiguration.current
    val isWide = config.screenWidthDp >= 900

    var selected by remember { mutableStateOf<String?>(if (isWide) "permissions" else null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var accessibilityOn by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var overlayOn by remember { mutableStateOf(PermissionHelper.canDrawOverlay(context)) }

    // 佔位狀態（後續接真實引擎）
    var clickMode by remember { mutableStateOf("單點") }
    var interval by remember { mutableStateOf(200) }
    var count by remember { mutableStateOf("無限") }
    var duration by remember { mutableStateOf(50) }
    var randomOffset by remember { mutableStateOf(false) }
    var randomInterval by remember { mutableStateOf(false) }
    var antiDetect by remember { mutableStateOf(false) }
    var floatButton by remember { mutableStateOf(false) }
    var showRemain by remember { mutableStateOf(true) }
    var draggable by remember { mutableStateOf(true) }
    var opacity by remember { mutableStateOf(90) }
    var floatSize by remember { mutableStateOf("中") }
    var themeDark by remember { mutableStateOf(SettingsColors.isDark) }

    // 初始化控制器預設值
    LaunchedEffect(Unit) {
        ClickController.attach(context)
        SettingsStore.init(context)
        ClickController.intervalMs = interval.toLong()
        ClickController.durationMs = duration.toLong()
        ClickController.count = if (count == "無限") -1 else count.toIntOrNull() ?: -1
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessibilityOn = PermissionHelper.isAccessibilityEnabled(context)
                overlayOn = PermissionHelper.canDrawOverlay(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                DetailPane(
                    selected = selected ?: "permissions",
                    accessibilityOn = accessibilityOn,
                    overlayOn = overlayOn,
                    clickMode = clickMode,
                    onClickMode = { v ->
                        clickMode = v
                        ClickController.mode = v
                    },
                    interval = interval,
                    onInterval = { v ->
                        interval = v
                        ClickController.intervalMs = v.toLong()
                        SettingsStore.save()
                    },
                    count = count,
                    onCount = { v ->
                        count = v
                        ClickController.count = if (v == "無限") -1 else v.toIntOrNull() ?: -1
                        SettingsStore.save()
                    },
                    duration = duration,
                    onDuration = { v ->
                        duration = v
                        ClickController.durationMs = v.toLong()
                        SettingsStore.save()
                    },
                    randomOffset = randomOffset,
                    onRandomOffset = { v ->
                        randomOffset = v
                        ClickController.randomOffset = v
                        SettingsStore.save()
                    },
                    randomInterval = randomInterval,
                    onRandomInterval = { v ->
                        randomInterval = v
                        ClickController.randomInterval = v
                        SettingsStore.save()
                    },
                    antiDetect = antiDetect,
                    onAntiDetect = { v ->
                        antiDetect = v
                        ClickController.antiDetect = v
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
                        }
                    },
                    showRemain = showRemain,
                    onShowRemain = { showRemain = it },
                    draggable = draggable,
                    onDraggable = { draggable = it },
                    opacity = opacity,
                    onOpacity = { opacity = it },
                    floatSize = floatSize,
                    onFloatSize = { floatSize = it },
                    onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
                    onOpenOverlay = { PermissionHelper.openOverlaySettings(context) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it / 3 } + fadeOut()
                    } else {
                        slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
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
                        DetailPane(
                            selected = page,
                            accessibilityOn = accessibilityOn,
                            overlayOn = overlayOn,
                            clickMode = clickMode,
                            onClickMode = { v ->
                        clickMode = v
                        ClickController.mode = v
                    },
                            interval = interval,
                            onInterval = { interval = it },
                            count = count,
                            onCount = { count = it },
                            duration = duration,
                            onDuration = { duration = it },
                            randomOffset = randomOffset,
                            onRandomOffset = { randomOffset = it },
                            randomInterval = randomInterval,
                            onRandomInterval = { randomInterval = it },
                            antiDetect = antiDetect,
                            onAntiDetect = { v ->
                        antiDetect = v
                        ClickController.antiDetect = v
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
                        }
                    },
                            showRemain = showRemain,
                            onShowRemain = { showRemain = it },
                            draggable = draggable,
                            onDraggable = { draggable = it },
                            opacity = opacity,
                            onOpacity = { opacity = it },
                            floatSize = floatSize,
                            onFloatSize = { floatSize = it },
                            onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(context) },
                            onOpenOverlay = { PermissionHelper.openOverlaySettings(context) },
                            modifier = Modifier.fillMaxSize()
                        )
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
    Column(modifier.background(SettingsColors.bg)) {
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
    showRemain: Boolean,
    onShowRemain: (Boolean) -> Unit,
    draggable: Boolean,
    onDraggable: (Boolean) -> Unit,
    opacity: Int,
    onOpacity: (Int) -> Unit,
    floatSize: String,
    onFloatSize: (String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(SettingsColors.bg)
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
                antiDetect, onAntiDetect
            )
            "sequence" -> SequenceDetail()
            "overlay" -> OverlayDetail(
                overlayOn, onOpenOverlay,
                floatButton, onFloatButton,
                showRemain, onShowRemain,
                draggable, onDraggable,
                opacity, onOpacity,
                floatSize, onFloatSize
            )
            "about" -> AboutDetail()
        }
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
    Caption("無障礙服務用於在其他應用中執行點擊，必須在系統設定中手動開啟。懸浮窗用於浮動控制面板。")
}

@Composable
fun ClickDetail(
    clickMode: String, onClickMode: (String) -> Unit,
    interval: Int, onInterval: (Int) -> Unit,
    count: String, onCount: (String) -> Unit,
    duration: Int, onDuration: (Int) -> Unit,
    randomOffset: Boolean, onRandomOffset: (Boolean) -> Unit,
    randomInterval: Boolean, onRandomInterval: (Boolean) -> Unit,
    antiDetect: Boolean, onAntiDetect: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Spacer(Modifier.height(12.dp))
    DetailTitle("點擊")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "基本") {
        CycleRow("模式", clickMode, listOf("單點", "多點", "滑動")) { onClickMode(it) }
        CycleRow("間隔", "$interval ms", listOf(50, 100, 200, 300, 500, 1000).map { "$it ms" }) { v ->
            onInterval(v.removeSuffix(" ms").toInt())
        }
        CycleRow("次數", count, listOf("無限", "10", "50", "100", "500")) { onCount(it) }
        CycleRow("暖機次數", ClickController.warmupClicks.toString(), listOf(0, 3, 5, 10).map { it.toString() }) { v ->
            ClickController.warmupClicks = v.toInt()
        }
        CycleRow("爆發次數", ClickController.burstCount.toString(), listOf(1, 2, 3, 5).map { it.toString() }) { v ->
            ClickController.burstCount = v.toInt()
        }
        CycleRow("最長時間", if (ClickController.maxMinutes == 0) "無限" else "${ClickController.maxMinutes} 分",
            listOf("無限", "1 分", "5 分", "10 分", "30 分")) { v ->
            ClickController.maxMinutes = when (v) {
                "無限" -> 0
                else -> v.removeSuffix(" 分").toIntOrNull() ?: 0
            }
        }
        CycleRow("開始延遲", "${ClickController.startDelayMs} ms", listOf(0, 500, 1000, 2000).map { "$it ms" }) { v ->
            ClickController.startDelayMs = v.removeSuffix(" ms").toLong()
        }
        SettingsStatusRow(
            "狀態",
            when {
                ClickController.isRunning -> "執行中 ${ClickController.elapsedSec()}s"
                !ClickAccessibilityService.isEnabled() -> "需開啟無障礙"
                else -> "停止"
            }
        ) {
            ClickController.toggle()
        }
        SettingsStatusRow("總點擊", ClickController.totalClicks.toString()) {
            ClickController.resetTotal()
        }
        SettingsStatusRow("暫停／繼續", if (ClickController.isPaused) "已暫停" else "") {
            if (ClickController.isPaused) ClickController.resume() else ClickController.pause()
        }
        SettingsStatusRow("單次測試", "") {
            val p = ClickController.points.firstOrNull() ?: return@SettingsStatusRow
            ClickAccessibilityService.instance?.performClick(p.first, p.second, ClickController.durationMs)
        }
            val p = ClickController.points.firstOrNull() ?: return@SettingsStatusRow
            ClickAccessibilityService.instance?.performClick(p.first, p.second, ClickController.durationMs)
        }
        SettingsStatusRow("測試滑動", "") {
            ClickAccessibilityService.instance?.performSwipe(400f, 1200f, 400f, 400f, 300)
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "快速預設") {
        SettingsStatusRow("慢速簽到", "500ms") {
            interval = 500
            ClickController.intervalMs = 500
            count = "10"
            ClickController.count = 10
            SettingsStore.save()
        }
        SettingsStatusRow("快速連點", "50ms") {
            interval = 50
            ClickController.intervalMs = 50
            count = "無限"
            ClickController.count = -1
            SettingsStore.save()
        }
        SettingsStatusRow("中速任務", "200ms") {
            interval = 200
            ClickController.intervalMs = 200
            count = "50"
            ClickController.count = 50
            SettingsStore.save()
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "座標") {
        SettingsStatusRow("點 1", pointsText(0)) {}
        SettingsStatusRow("點 2", pointsText(1)) {}
        SettingsStatusRow("點 3", pointsText(2)) {}
        CycleRow("最後點 X", ClickController.points.lastOrNull()?.first?.toInt()?.toString() ?: "540",
            listOf(200, 400, 540, 700, 900).map { it.toString() }) { v ->
            val y = ClickController.points.lastOrNull()?.second ?: 960f
            if (ClickController.points.isNotEmpty()) {
                ClickController.points[ClickController.points.lastIndex] = v.toFloat() to y
            }
        }
        CycleRow("最後點 Y", ClickController.points.lastOrNull()?.second?.toInt()?.toString() ?: "960",
            listOf(400, 700, 960, 1200, 1600).map { it.toString() }) { v ->
            val x = ClickController.points.lastOrNull()?.first ?: 540f
            if (ClickController.points.isNotEmpty()) {
                ClickController.points[ClickController.points.lastIndex] = x to v.toFloat()
            }
        }
        SettingsStatusRow("新增點", "") {
            ClickController.addPoint(540f, 960f)
        }
        SettingsStatusRow("儲存為常用", "") {
            ClickController.saveFavorite()
        }
        SettingsStatusRow("清空常用", "") {
            ClickController.clearFavorites()
        }
        SettingsStatusRow("複製最後點", "") {
            ClickController.duplicateLastPoint()
        }
        if (ClickController.favoritePoints.isNotEmpty()) {
            ClickController.favoritePoints.forEachIndexed { i, p ->
                SettingsStatusRow("常用 ${i + 1}", "${p.first.toInt()},${p.second.toInt()}") {
                    ClickController.loadFavorite(i)
                }
            }
        }
        SettingsStatusRow("微調 +10", "") {
            ClickController.nudgeLastPoint(10f, 0f)
        }
        SettingsStatusRow("微調 -10", "") {
            ClickController.nudgeLastPoint(-10f, 0f)
        }
        SettingsStatusRow("清空點", "") {
            ClickController.clearPoints()
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "進階") {
        ToggleRow("執行時隱藏懸浮窗", ClickController.autoHideOverlay) {
            ClickController.autoHideOverlay = it
        }
        ToggleRow("開始提示", ClickController.showToast) {
            ClickController.showToast = it
        }
        ToggleRow("結束提示音", ClickController.endBeep) {
            ClickController.endBeep = it
        }
        ToggleRow("慢啟動", ClickController.slowStart) {
            ClickController.slowStart = it
        }
        ToggleRow("記錄點擊", ClickController.logClicks) {
            ClickController.logClicks = it
        }
        ToggleRow("失敗即停", ClickController.stopOnError) {
            ClickController.stopOnError = it
        }
        ToggleRow("擬人化", ClickController.humanize) {
            ClickController.humanize = it
        }
        ToggleRow("隨機偏移", randomOffset) { onRandomOffset(it) }
        ToggleRow("隨機間隔", randomInterval) { onRandomInterval(it) }
        ToggleRow("防偵測延遲", antiDetect) { onAntiDetect(it) }
        ToggleRow("開始震動", ClickController.vibrateOnStart) {
            ClickController.vibrateOnStart = it
        }
        ToggleRow("保持螢幕", ClickController.keepScreenOn) {
            ClickController.keepScreenOn = it
            if (it) {
                (context as? android.app.Activity)?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                (context as? android.app.Activity)?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        CycleRow("CPS 上限", ClickController.maxCps.toString(), listOf(5, 10, 20, 30, 50).map { it.toString() }) { v ->
            ClickController.maxCps = v.toInt()
        }
        ToggleRow("按下即點", ClickController.clickOnDown) {
            ClickController.clickOnDown = it
        }
        ToggleRow("開始清空記錄", ClickController.clearLogOnStart) {
            ClickController.clearLogOnStart = it
        }
        ToggleRow("點擊音效", ClickController.soundOnClick) {
            ClickController.soundOnClick = it
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "記錄") {
        if (ClickController.log.isEmpty()) {
            SettingsStatusRow("尚無記錄", "") {}
        } else {
            ClickController.log.take(5).forEach { line ->
                SettingsStatusRow(line, "") {}
            }
            SettingsStatusRow("清空記錄", "") {
                ClickController.log.clear()
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Caption("點擊列可循環切換數值。以上為佔位狀態，後續將連接實際點擊引擎。")
}

@Composable
fun SequenceDetail() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Spacer(Modifier.height(12.dp))
    DetailTitle("序列")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "錄製") {
        SettingsStatusRow("開始錄製", if (SequenceRecorder.isRecording) "錄製中" else "") {
            SequenceRecorder.start()
        }
        SettingsStatusRow("加入測試點", "") {
            SequenceRecorder.addClick(540f, 960f, ClickController.intervalMs)
        }
        SettingsStatusRow("加入等待 500ms", "") {
            SequenceRecorder.addWait(500)
        }
        SettingsStatusRow("加入測試滑動", "") {
            SequenceRecorder.addSwipe(400f, 1200f, 400f, 400f, 300)
        }
        SettingsStatusRow("停止並儲存", "${SequenceRecorder.steps.size} 步") {
            SequenceRecorder.stop()
            SequenceRecorder.save("未命名 ${SequenceRecorder.saved.size + 1}")
        }
        SettingsStatusRow("回放目前", if (SequenceRecorder.isReplaying) "回放中" else "") {
            if (SequenceRecorder.isReplaying) SequenceRecorder.stopReplay() else SequenceRecorder.replay()
        }
        CycleRow("回放次數", if (SequenceRecorder.replayLoops == 0) "無限" else SequenceRecorder.replayLoops.toString(),
            listOf("1", "3", "5", "無限")) { v ->
            SequenceRecorder.replayLoops = if (v == "無限") 0 else v.toInt()
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
        SettingsStatusRow("保留前 10 步", "") {
            SequenceRecorder.trimTo(10)
        }
        SettingsStatusRow("複製最後一步", "") {
            if (SequenceRecorder.steps.isNotEmpty()) {
                SequenceRecorder.duplicateStep(SequenceRecorder.steps.lastIndex)
            }
        }
        SettingsStatusRow("打亂步驟", "") {
            SequenceRecorder.shuffle()
        }
        SettingsStatusRow("反轉步驟", "") {
            SequenceRecorder.reverse()
        }
        SettingsStatusRow("上移最後一步", "") {
            if (SequenceRecorder.steps.isNotEmpty()) {
                SequenceRecorder.moveUp(SequenceRecorder.steps.lastIndex)
            }
        }
        SettingsStatusRow("清空目前", "") {
            SequenceRecorder.clear()
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "目前步驟") {
        if (SequenceRecorder.steps.isEmpty()) {
            SettingsStatusRow("尚無步驟", "") {}
        } else {
            SequenceRecorder.steps.forEachIndexed { i, step ->
                val label = when (step.type) {
                    "click" -> "點擊 ${step.x.toInt()},${step.y.toInt()}"
                    "swipe" -> "滑動 ${step.x.toInt()},${step.y.toInt()}→${step.x2.toInt()},${step.y2.toInt()}"
                    "wait" -> "等待 ${step.delayMs}ms"
                    else -> step.type
                }
                SettingsStatusRow("$i $label", "刪") {
                    SequenceRecorder.removeAt(i)
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "已儲存") {
        if (SequenceRecorder.saved.isEmpty()) {
            SettingsStatusRow("尚無序列", "") {}
        } else {
            SequenceRecorder.saved.keys.forEach { name ->
                SettingsStatusRow(name, "${SequenceRecorder.saved[name]?.size ?: 0} 步") {
                    SequenceRecorder.load(name)
                }
            }
        }
        SettingsStatusRow("複製目前為新序列", "") {
            SequenceRecorder.duplicateCurrent("副本 ${SequenceRecorder.saved.size + 1}")
        }
        SettingsStatusRow("清空已儲存", "") {
            SequenceRecorder.clearSaved()
        }
    }

    Spacer(Modifier.height(12.dp))
    Caption("目前 ${SequenceRecorder.steps.size} 步。點擊步驟可刪除。")
}

@Composable
fun OverlayDetail(
    overlayOn: Boolean, onOpenOverlay: () -> Unit,
    floatButton: Boolean, onFloatButton: (Boolean) -> Unit,
    showRemain: Boolean, onShowRemain: (Boolean) -> Unit,
    draggable: Boolean, onDraggable: (Boolean) -> Unit,
    opacity: Int, onOpacity: (Int) -> Unit,
    floatSize: String, onFloatSize: (String) -> Unit
) {
    Spacer(Modifier.height(12.dp))
    DetailTitle("懸浮窗")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "顯示") {
        SettingsStatusRow("狀態", if (overlayOn) "權限已開" else "未授權", onOpenOverlay)
        ToggleRow("浮動按鈕", floatButton) { onFloatButton(it) }
        CycleRow("透明度", "$opacity%", listOf(60, 75, 90, 100).map { "$it%" }) { v ->
            onOpacity(v.removeSuffix("%").toInt())
        }
        CycleRow("大小", floatSize, listOf("小", "中", "大")) { onFloatSize(it) }
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "操作") {
        ToggleRow("顯示開始／停止", true) {}
        ToggleRow("顯示剩餘次數", showRemain) { onShowRemain(it) }
        ToggleRow("可拖曳", draggable) { onDraggable(it) }
        ToggleRow("懸浮窗顯示座標", ClickController.showCoordsOnOverlay) {
            ClickController.showCoordsOnOverlay = it
        }
    }

    Spacer(Modifier.height(12.dp))
    Caption("單擊開始／停止點擊。雙擊回放序列。長按切換錄製。")
}

@Composable
fun AboutDetail() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var themeDark by remember { mutableStateOf(SettingsColors.isDark) }
    Spacer(Modifier.height(12.dp))
    DetailTitle("關於")
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "應用") {
        SettingsStatusRow("版本", "1.2.0") {}
        SettingsStatusRow("立即停止", "") {
            ClickController.stop()
            SequenceRecorder.stopReplay()
        }
        SettingsStatusRow("重設預設", "") {
            ClickController.resetDefaults()
            SettingsStore.save()
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
        }
        SettingsStatusRow("切換主題", if (themeDark) "深色" else "淺色") {
            themeDark = !themeDark
            SettingsColors.isDark = themeDark
        }
        SettingsStatusRow("授權", "MIT") {}
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "功能") {
        SettingsStatusRow("連點引擎", "已實作") {}
        SettingsStatusRow("懸浮窗", "已實作") {}
        SettingsStatusRow("序列回放", "已實作") {}
        SettingsStatusRow("自動錄製", "已實作") {}
        SettingsStatusRow("執行通知", "已實作") {}
    }

    Spacer(Modifier.height(8.dp))

    SettingsSection(label = "原則") {
        SettingsStatusRow("無廣告", "是") {}
        SettingsStatusRow("無追蹤", "是") {}
        SettingsStatusRow("開源", "是") {}
    }

    Spacer(Modifier.height(12.dp))
    Caption("極簡、免廣告、高對比的自動點擊工具。")
}

private fun pointsText(index: Int): String {
    val p = ClickController.points.getOrNull(index) ?: return "未設定"
    return "${p.first.toInt()}, ${p.second.toInt()}"
}
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
    var pressed by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (pressed) SettingsColors.circleBtnPressed else SettingsColors.circleBtn)
                .clickable { onBack() },
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
            modifier = Modifier
                .weight(1f)
                .padding(end = 32.dp),
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
                .clip(RoundedCornerShape(10.dp))
                .background(SettingsColors.card)
                .border(
                    width = 1.dp,
                    color = SettingsColors.cardBorder,
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) SettingsColors.selected else Color.Transparent)
            .clickable { onClick() }
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
fun SettingsStatusRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
fun CycleRow(
    label: String,
    current: String,
    options: List<String>,
    onChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
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
fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
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
        Text(
            if (checked) "開啟" else "關閉",
            style = TextStyle(fontSize = 16.sp, color = SettingsColors.secondary)
        )
    }
}
