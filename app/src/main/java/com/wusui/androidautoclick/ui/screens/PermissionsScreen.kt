package com.wusui.androidautoclick.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.service.PermissionHelper
import com.wusui.androidautoclick.ui.AppColors
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow

@Composable
fun PermissionsScreen(a11y: Boolean, overlay: Boolean) {
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT < 33) true
            else context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("必要權限（皆需手動）")
        SettingsCard {
            SettingsRow(
                title = "無障礙服務",
                subtitle = "路徑：設定 → 無障礙 → 已安裝的應用／服務 → AutoClick → 開啟。系統禁止 App 自動開啟。",
                trailing = if (a11y) "已開" else "去開啟",
                showArrow = true,
                onClick = { PermissionHelper.openAccessibilitySettings(context) }
            )
            SettingsRow(
                title = "懸浮窗",
                subtitle = "路徑：設定 → 應用 → AutoClick → 顯示在其他應用上層。",
                trailing = if (overlay) "已開" else "去開啟",
                showArrow = true,
                onClick = { PermissionHelper.openOverlaySettings(context) }
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("建議權限")
        SettingsCard {
            SettingsRow(
                title = "通知（執行狀態）",
                subtitle = if (Build.VERSION.SDK_INT < 33) "系統已允許"
                else "顯示連點剩餘次數；關閉也不影響點擊本身。",
                trailing = if (notifGranted) "已開" else "去開啟",
                showArrow = Build.VERSION.SDK_INT >= 33 && !notifGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "開啟後請回到本 App；總覽會自動刷新狀態。未開無障礙時按開始會顯示明確錯誤。",
            color = AppColors.secondary(),
            fontSize = 12.sp
        )
    }
}
