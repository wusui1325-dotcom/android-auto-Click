package com.wusui.androidautoclick.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wusui.androidautoclick.data.AppStore
import com.wusui.androidautoclick.engine.SessionType
import com.wusui.androidautoclick.service.OverlayService
import com.wusui.androidautoclick.service.PermissionHelper
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SettingsTheme {
                HomeScreen()
            }
        }
        updateBarIcons()
    }

    override fun onResume() {
        super.onResume()
        updateBarIcons()
    }

    private fun updateBarIcons() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    var a11y by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var overlay by remember { mutableStateOf(PermissionHelper.canDrawOverlay(context)) }
    var pickType by remember { mutableStateOf(false) }
    val barVertical by AppStore.barVertical.collectAsState()
    val ready = a11y && overlay

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

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "AUTO-C",
            color = AppColors.text(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em
        )
        Spacer(Modifier.height(4.dp))
        Text("3.0.1 · 懸浮控制", color = AppColors.secondary(), fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        SectionLabel("權限")
        SettingsCard {
            SettingsRow(
                title = "無障礙服務",
                subtitle = if (a11y) "已開啟" else "設定 → 無障礙 → AutoClick",
                trailing = if (a11y) "✓" else "去開啟",
                showArrow = !a11y,
                onClick = { if (!a11y) PermissionHelper.openAccessibilitySettings(context) }
            )
            SettingsRow(
                title = "懸浮窗",
                subtitle = if (overlay) "已開啟" else "顯示控制列與觸控點",
                trailing = if (overlay) "✓" else "去開啟",
                showArrow = !overlay,
                onClick = { if (!overlay) PermissionHelper.openOverlaySettings(context) }
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("控制列")
        SettingsCard {
            SettingsRow(
                title = "排列方向",
                subtitle = if (barVertical) "目前：豎排" else "目前：橫排",
                trailing = if (barVertical) "改橫排" else "改豎排",
                showArrow = true,
                onClick = { AppStore.setBarVertical(!barVertical) }
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("開始")
        SettingsCard {
            if (OverlayService.isRunning) {
                SettingsRow(
                    title = "控制列運行中",
                    subtitle = "可回桌面繼續操作；或在此關閉",
                    trailing = "關閉",
                    showArrow = true,
                    onClick = {
                        context.startService(
                            Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE)
                        )
                    }
                )
            } else {
                SettingsRow(
                    title = "開始 AUTO-C",
                    subtitle = if (ready) "選擇單點／多點／錄製" else "請先開權限",
                    trailing = "開始",
                    showArrow = true,
                    onClick = {
                        if (!ready) return@SettingsRow
                        pickType = true
                    }
                )
            }
        }

        if (pickType && ready && !OverlayService.isRunning) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("開始類型")
            SettingsCard {
                SessionType.entries.forEach { type ->
                    SettingsRow(
                        title = type.label(),
                        subtitle = when (type) {
                            SessionType.SINGLE -> "一個可拖觸控點"
                            SessionType.MULTI -> "多點＋拖拽組"
                            SessionType.RECORD -> "錄製點擊（自繪畫面需手動加點）"
                        },
                        showArrow = true,
                        onClick = {
                            pickType = false
                            val i = Intent(context, OverlayService::class.java)
                                .putExtra(OverlayService.EXTRA_TYPE, type.name)
                            ContextCompat.startForegroundService(context, i)
                        }
                    )
                }
                SettingsRow(
                    title = "取消",
                    onClick = { pickType = false }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("說明")
        SettingsCard {
            SettingsRow(
                title = "操作概要",
                subtitle = "控制列以中心拖動（收起亦可）。觸控點短按只定位；長按調間隔／次數。末點「與下次間隔」＝下一輪循環等待。"
            )
        }
    }
}
