package com.wusui.androidautoclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
        setContent {
            SettingsTheme {
                AppShell()
            }
        }
    }
}

private object SettingsColors {
    const val isDark = true

    val bg: Color get() = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val text: Color get() = if (isDark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F)
    val secondary: Color get() = if (isDark) Color(0xFF8E8E93) else Color(0xFF6E6E73)
    val divider: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFD1D1D6)
    val selected: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val card: Color get() = if (isDark) Color(0xFF111111) else Color.White
    val cardBorder: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFD1D1D6)
    val rowActive: Color get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
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
                        slideInHorizontally { fullWidth -> fullWidth } + fadeIn() togetherWith
                            slideOutHorizontally { fullWidth -> -fullWidth / 3 } + fadeOut()
                    } else {
                        slideInHorizontally { fullWidth -> -fullWidth / 3 } + fadeIn() togetherWith
                            slideOutHorizontally { fullWidth -> fullWidth } + fadeOut()
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
                        DetailTopBar(
                            title = when (page) {
                                "permissions" -> "權限"
                                "click" -> "點擊"
                                "about" -> "關於"
                                else -> page
                            },
                            onBack = { selected = null }
                        )
                        DetailPane(
                            selected = page,
                            accessibilityOn = accessibilityOn,
                            overlayOn = overlayOn,
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
            SettingsRow(
                label = "權限",
                selected = selected == "permissions",
                onClick = { onSelect("permissions") }
            )
            SettingsRow(
                label = "點擊",
                selected = selected == "click",
                onClick = { onSelect("click") }
            )
        }

        Spacer(Modifier.height(8.dp))

        SettingsSection(label = "其他") {
            SettingsRow(
                label = "關於",
                selected = selected == "about",
                onClick = { onSelect("about") }
            )
        }
    }
}

@Composable
fun DetailPane(
    selected: String,
    accessibilityOn: Boolean,
    overlayOn: Boolean,
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
            "permissions" -> PermissionsDetail(
                accessibilityOn = accessibilityOn,
                overlayOn = overlayOn,
                onOpenAccessibility = onOpenAccessibility,
                onOpenOverlay = onOpenOverlay
            )
            "click" -> ClickDetail()
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
    Text(
        "權限",
        style = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight(650),
            color = SettingsColors.text
        )
    )
    Spacer(Modifier.height(16.dp))

    SettingsSection(label = "必要") {
        SettingsStatusRow(
            label = "無障礙服務",
            value = if (accessibilityOn) "已開啟" else "未開啟",
            onClick = onOpenAccessibility
        )
        SettingsStatusRow(
            label = "懸浮窗",
            value = if (overlayOn) "已開啟" else "未開啟",
            onClick = onOpenOverlay
        )
    }

    Spacer(Modifier.height(12.dp))
    Text(
        "無障礙服務用於在其他應用中執行點擊，必須在系統設定中手動開啟。懸浮窗用於後續浮動控制面板。",
        style = TextStyle(
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = SettingsColors.secondary
        ),
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun ClickDetail() {
    Spacer(Modifier.height(12.dp))
    Text(
        "點擊",
        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight(650), color = SettingsColors.text)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "點擊設定與錄製將在下一步加入。",
        style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = SettingsColors.secondary)
    )
}

@Composable
fun AboutDetail() {
    Spacer(Modifier.height(12.dp))
    Text(
        "關於",
        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight(650), color = SettingsColors.text)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "極簡、免廣告、高對比的自動點擊工具。",
        style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = SettingsColors.secondary)
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
        Text(
            value,
            style = TextStyle(fontSize = 16.sp, color = SettingsColors.secondary)
        )
        Spacer(Modifier.width(6.dp))
        Text("›", color = SettingsColors.arrow, fontSize = 18.sp)
    }
}
