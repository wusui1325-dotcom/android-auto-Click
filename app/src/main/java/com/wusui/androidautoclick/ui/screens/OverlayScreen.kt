package com.wusui.androidautoclick.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.service.OverlayService
import com.wusui.androidautoclick.service.PermissionHelper
import com.wusui.androidautoclick.ui.AppColors
import com.wusui.androidautoclick.ui.components.ActionButton
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow
import com.wusui.androidautoclick.ui.components.SwitchRow

@Composable
fun OverlayScreen(overlayGranted: Boolean) {
    val context = LocalContext.current
    val cfg by SettingsRepository.config.collectAsState()
    var running by remember { mutableStateOf(OverlayService.isRunning) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                running = OverlayService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun startOverlay() {
        if (!PermissionHelper.canDrawOverlay(context)) {
            PermissionHelper.openOverlaySettings(context)
            return
        }
        val i = Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW)
        androidx.core.content.ContextCompat.startForegroundService(context, i)
        running = true
    }

    fun stopOverlay() {
        val i = Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE)
        context.startService(i)
        running = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("懸浮窗")
        SettingsCard {
            SettingsRow("權限", trailing = if (overlayGranted) "已開" else "未開")
            SettingsRow("服務", trailing = if (running) "運行中" else "已停止")
            SwitchRow("顯示標記點", "可拖曳調整座標（中心點＝點擊點）", cfg.showMarkers) {
                SettingsRepository.updateConfig { c -> c.copy(showMarkers = it) }
            }
            SwitchRow("精簡懸浮選單", "長按選單優先顯示序列快捷", cfg.compactOverlay) {
                SettingsRepository.updateConfig { c -> c.copy(compactOverlay = it) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "短按：起停。長按：精簡選單（序列快捷／＋點／標記）。拖曳標記改座標。",
            color = AppColors.secondary(),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))
        ActionButton(
            label = if (running) "關閉懸浮窗" else "開啟懸浮窗",
            onClick = { if (running) stopOverlay() else startOverlay() },
            danger = running
        )
    }
}
