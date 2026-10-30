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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.engine.ClickEngine
import com.wusui.androidautoclick.engine.ClickMode
import com.wusui.androidautoclick.service.OverlayService
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
    val saved by SettingsRepository.savedSequences.collectAsState()
    val status by ClickEngine.status.collectAsState()
    val stats by SettingsRepository.stats.collectAsState()
    val context = LocalContext.current
    val ready = a11y && overlay

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (!ready) {
            SectionLabel("開始之前")
            SettingsCard {
                if (!a11y) {
                    SettingsRow(
                        title = "① 開啟無障礙服務",
                        subtitle = "設定 → 無障礙 → 已安裝的服務 → AutoClick。系統禁止自動開。",
                        trailing = "去開啟",
                        showArrow = true,
                        onClick = { PermissionHelper.openAccessibilitySettings(context) }
                    )
                }
                if (!overlay) {
                    SettingsRow(
                        title = "② 開啟懸浮窗權限",
                        subtitle = "用來顯示浮動控制與可拖標記。",
                        trailing = "去開啟",
                        showArrow = true,
                        onClick = { PermissionHelper.openOverlaySettings(context) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        } else if (cfg.mode != ClickMode.SEQUENCE && points.isEmpty()) {
            SectionLabel("引導")
            SettingsCard {
                SettingsRow(
                    "還沒有標記點",
                    subtitle = "開懸浮窗 → 長按選單 → ＋點，或到「點擊設定」新增。"
                )
            }
            Spacer(Modifier.height(12.dp))
        } else if (cfg.mode == ClickMode.SEQUENCE && steps.isEmpty()) {
            SectionLabel("引導")
            SettingsCard {
                SettingsRow(
                    "序列還是空的",
                    subtitle = if (saved.isEmpty())
                        "到「序列編輯」用標記建步，或錄製標準 UI 點擊。"
                    else
                        "下面可快捷載入已存序列，或到序列編輯新增。"
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionLabel("快捷")
        SettingsCard {
            SettingsRow(
                title = if (OverlayService.isRunning) "懸浮窗已開" else "開啟懸浮窗",
                subtitle = "短按起停 · 長按選單（含序列快捷）",
                trailing = if (OverlayService.isRunning) "運行中" else "開啟",
                showArrow = !OverlayService.isRunning,
                onClick = {
                    if (!PermissionHelper.canDrawOverlay(context)) {
                        PermissionHelper.openOverlaySettings(context)
                        return@SettingsRow
                    }
                    if (!OverlayService.isRunning) {
                        val i = Intent(context, OverlayService::class.java)
                            .setAction(OverlayService.ACTION_SHOW)
                        ContextCompat.startForegroundService(context, i)
                    }
                }
            )
        }

        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("序列快捷")
            SettingsCard {
                saved.keys.sorted().take(6).forEach { name ->
                    SettingsRow(
                        title = name,
                        subtitle = "${saved[name]?.size ?: 0} 步" +
                            if (name == cfg.activeSequenceName) " · 目前" else "",
                        trailing = "載入",
                        showArrow = true,
                        onClick = { SettingsRepository.switchToSavedSequence(name) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("狀態")
        SettingsCard {
            SettingsRow("無障礙", trailing = if (a11y) "已開" else "未開")
            SettingsRow("懸浮窗權限", trailing = if (overlay) "已開" else "未開")
            SettingsRow("模式", trailing = cfg.mode.label())
            if (cfg.activeSequenceName.isNotBlank()) {
                SettingsRow("作用序列", trailing = cfg.activeSequenceName)
            }
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
            if (status.message.isNotBlank()) {
                SettingsRow("訊息", trailing = status.message)
            }
            SettingsRow("本場動作", trailing = "${status.totalActions}")
            SettingsRow("剩餘", trailing = status.remaining)
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("統計")
        SettingsCard {
            SettingsRow("累計動作", trailing = "${stats.lifetimeActions}")
            SettingsRow("累計場次", trailing = "${stats.lifetimeSessions}")
            SettingsRow("上一場", trailing = stats.lastSessionLabel())
        }

        if (status.lastError.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status.lastError, color = AppColors.danger, fontSize = 13.sp)
            SettingsRow("清除錯誤", onClick = { ClickEngine.clearError() })
        }
        Spacer(Modifier.height(16.dp))
        ActionButton(
            label = if (status.active) "停止" else "開始",
            onClick = { ClickEngine.toggle() },
            danger = status.active
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "間隔 ${cfg.intervalMs}ms · 時長 ${cfg.durationMs}ms · " +
                if (cfg.count < 0) "無限次" else "${cfg.count} 次",
            color = AppColors.secondary(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Spacer(Modifier.height(24.dp))
    }
}
