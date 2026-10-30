package com.wusui.androidautoclick.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.service.PermissionHelper
import com.wusui.androidautoclick.ui.components.CircleBack
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow
import com.wusui.androidautoclick.ui.screens.AboutScreen
import com.wusui.androidautoclick.ui.screens.ClickScreen
import com.wusui.androidautoclick.ui.screens.OverlayScreen
import com.wusui.androidautoclick.ui.screens.OverviewScreen
import com.wusui.androidautoclick.ui.screens.PermissionsScreen
import com.wusui.androidautoclick.ui.screens.SequenceScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Official Compose edge-to-edge: enable before setContent; do NOT set statusBarColor
        // (deprecated / no-op on Android 15+). Draw app bg behind bars; pad with WindowInsets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SettingsTheme {
                AppShell(onThemeChange = { updateBarIcons() })
            }
        }
        updateBarIcons()
    }

    override fun onResume() {
        super.onResume()
        updateBarIcons()
    }

    /** Contrast icons with our black / #F2F2F7 page background. */
    private fun updateBarIcons() {
        val dark = SettingsRepository.config.value.darkTheme
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // dark theme → light icons; light theme → dark icons
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}

private data class NavItem(val id: String, val title: String, val section: String)

private val NAV = listOf(
    NavItem("overview", "總覽", "快捷"),
    NavItem("permissions", "權限", "快捷"),
    NavItem("click", "點擊設定", "操作"),
    NavItem("sequence", "序列編輯", "操作"),
    NavItem("overlay", "懸浮控制", "操作"),
    NavItem("about", "關於", "其他")
)

@Composable
fun AppShell(onThemeChange: () -> Unit = {}) {
    val config = LocalConfiguration.current
    val isWide = config.screenWidthDp >= 900
    var selected by remember { mutableStateOf<String?>(if (isWide) "overview" else null) }
    val context = LocalContext.current
    var a11y by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var overlay by remember { mutableStateOf(PermissionHelper.canDrawOverlay(context)) }
    val cfg by SettingsRepository.config.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                a11y = PermissionHelper.isAccessibilityEnabled(context)
                overlay = PermissionHelper.canDrawOverlay(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg())
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (isWide) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        "AutoClick",
                        color = AppColors.text(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em
                    )
                    Spacer(Modifier.height(16.dp))
                    MenuList(selected, onSelect = { selected = it })
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(AppColors.divider())
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    Text(
                        NAV.find { it.id == selected }?.title ?: "",
                        color = AppColors.text(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(16.dp))
                    Detail(selected, a11y, overlay, onThemeChange)
                }
            }
        } else {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "nav"
            ) { page ->
                if (page == null) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        Text(
                            "AutoClick",
                            color = AppColors.text(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (cfg.darkTheme) "深色 · 2.0.1" else "明亮 · 2.0.1",
                            color = AppColors.secondary(),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        MenuList(null, onSelect = { selected = it })
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircleBack { selected = null }
                            Spacer(Modifier.weight(1f))
                            Text(
                                NAV.find { it.id == page }?.title ?: "",
                                color = AppColors.text(),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Spacer(Modifier.width(36.dp))
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(AppColors.divider())
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            Detail(page, a11y, overlay, onThemeChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuList(selected: String?, onSelect: (String) -> Unit) {
    val groups = NAV.groupBy { it.section }
    groups.forEach { (section, items) ->
        SectionLabel(section)
        SettingsCard {
            items.forEach { item ->
                SettingsRow(
                    title = item.title,
                    showArrow = true,
                    trailing = if (selected == item.id) "●" else null,
                    onClick = { onSelect(item.id) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Detail(
    id: String?,
    a11y: Boolean,
    overlay: Boolean,
    onThemeChange: () -> Unit
) {
    when (id) {
        "overview" -> OverviewScreen(a11y, overlay)
        "permissions" -> PermissionsScreen(a11y, overlay)
        "click" -> ClickScreen()
        "sequence" -> SequenceScreen()
        "overlay" -> OverlayScreen(overlay)
        "about" -> AboutScreen(onThemeChange)
        else -> OverviewScreen(a11y, overlay)
    }
}
