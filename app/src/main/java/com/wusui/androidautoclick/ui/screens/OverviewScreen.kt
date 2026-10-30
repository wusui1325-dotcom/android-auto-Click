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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.engine.ClickEngine
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("狀態")
        SettingsCard {
            SettingsRow("無障礙", trailing = if (a11y) "已開" else "未開")
            SettingsRow("懸浮窗", trailing = if (overlay) "已開" else "未開")
            SettingsRow("模式", trailing = cfg.mode.label())
            SettingsRow("點數", trailing = "${points.size}")
            SettingsRow("序列步驟", trailing = "${steps.size}")
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
