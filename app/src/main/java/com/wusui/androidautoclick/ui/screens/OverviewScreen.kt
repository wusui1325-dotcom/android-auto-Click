package com.wusui.androidautoclick.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.engine.ClickEngine
import com.wusui.androidautoclick.service.PermissionHelper
import com.wusui.androidautoclick.ui.AppColors
import com.wusui.androidautoclick.ui.components.ActionButton
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow

@Composable
fun OverviewScreen(a11y: Boolean, overlay: Boolean) {
    val cfg by SettingsRepository.config.collectAsState()
    val points by SettingsRepository.points.collectAsState()
    val steps by SettingsRepository.sequenceSteps.collectAsState()
    val status by ClickEngine.status.collectAsState()
    val context = LocalContext.current
    val ready = a11y && overlay

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (!ready) {
            SectionLabel("先完成這些（需手動開啟）")
            SettingsCard {
                if (!a11y) {
                    SettingsRow(
                        title = "開啟無障礙服務",
                        subtitle = "系統設定 → 已安裝的服務 → AutoClick。無法自動開啟。",
                        trailing = "去開啟",
                        showArrow = true,
                        onClick = { PermissionHelper.openAccessibilitySettings(context) }
                    )
                }
                if (!overlay) {
                    SettingsRow(
                        title = "開啟懸浮窗權限",
                        subtitle = "顯示浮動控制與可拖標記。無法自動開啟。",
                        trailing = "去開啟",
                        showArrow = true,
                        onClick = { PermissionHelper.openOverlaySettings(context) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionLabel("狀態")
        SettingsCard {
            SettingsRow("無障礙", trailing = if (a11y) "已開" else "未開")
            SettingsRow("懸浮窗", trailing = if (overlay) "已開" else "未開")
            SettingsRow("模式", trailing = cfg.mode.label())
            SettingsRow("點數", trailing = "${points.size}")
            SettingsRow("序列步驟", trailing = "${steps.size}")
            SettingsRow(
                "執行",
                trailing = when {
                    status.recording -> "錄製中"
                    status.replaying -> "回放中"
                    status.running -> "連點中"
                    else -> "閒置"
                }
            )
            SettingsRow("已執行", trailing = "${status.totalActions}")
            SettingsRow("剩餘", trailing = status.remaining)
        }
        if (status.lastError.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status.lastError, color = AppColors.danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        ActionButton(
            label = if (status.running || status.replaying) "停止" else "開始",
            onClick = { ClickEngine.toggle() },
            danger = status.running || status.replaying
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "間隔 ${cfg.intervalMs}ms · 時長 ${cfg.durationMs}ms · " +
                if (cfg.count < 0) "無限次" else "${cfg.count} 次",
            color = AppColors.secondary(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
