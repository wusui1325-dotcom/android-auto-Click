package com.wusui.androidautoclick.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.engine.ClickEngine
import com.wusui.androidautoclick.engine.ClickMode
import com.wusui.androidautoclick.ui.AppColors
import com.wusui.androidautoclick.ui.components.ActionButton
import com.wusui.androidautoclick.ui.components.NumberField
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow
import com.wusui.androidautoclick.ui.components.SwitchRow

@Composable
fun ClickScreen() {
    val cfg by SettingsRepository.config.collectAsState()
    val points by SettingsRepository.points.collectAsState()
    val status by ClickEngine.status.collectAsState()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("模式")
        SettingsCard {
            ClickMode.entries.forEach { mode ->
                SettingsRow(
                    title = mode.label(),
                    subtitle = when (mode) {
                        ClickMode.SINGLE -> "只點第一個標記"
                        ClickMode.MULTI -> "依序輪流多點"
                        ClickMode.SWIPE -> "相鄰點之間滑動"
                        ClickMode.SEQUENCE -> "回放錄製／編輯的序列"
                    },
                    trailing = if (cfg.mode == mode) "✓" else null,
                    onClick = { SettingsRepository.updateConfig { it.copy(mode = mode) } }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("參數")
        SettingsCard {
            ParamRow("間隔", cfg.intervalMs.toString(), "ms") { v ->
                v.toLongOrNull()?.let { n ->
                    SettingsRepository.updateConfig { it.copy(intervalMs = n.coerceIn(20, 10_000)) }
                }
            }
            ParamRow("按壓時長", cfg.durationMs.toString(), "ms") { v ->
                v.toLongOrNull()?.let { n ->
                    SettingsRepository.updateConfig { it.copy(durationMs = n.coerceIn(10, 5_000)) }
                }
            }
            ParamRow(
                "次數",
                if (cfg.count < 0) "" else cfg.count.toString(),
                "空=無限"
            ) { v ->
                val n = if (v.isBlank()) -1 else v.toIntOrNull() ?: return@ParamRow
                SettingsRepository.updateConfig { it.copy(count = n) }
            }
            ParamRow("啟動延遲", cfg.startDelayMs.toString(), "ms") { v ->
                v.toLongOrNull()?.let { n ->
                    SettingsRepository.updateConfig { it.copy(startDelayMs = n.coerceAtLeast(0)) }
                }
            }
            SwitchRow("座標抖動", "每次點擊微移 ±8px", cfg.randomOffset) {
                SettingsRepository.updateConfig { c -> c.copy(randomOffset = it) }
            }
            SwitchRow("間隔抖動", "間隔 ±30ms", cfg.randomInterval) {
                SettingsRepository.updateConfig { c -> c.copy(randomInterval = it) }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("標記點（${points.size}）")
        SettingsCard {
            points.forEachIndexed { i, p ->
                SettingsRow("點 ${i + 1}", trailing = "${p.x.toInt()}, ${p.y.toInt()}")
            }
            SettingsRow(
                title = "新增螢幕中央點",
                showArrow = true,
                onClick = {
                    val dm = context.resources.displayMetrics
                    SettingsRepository.addPoint(dm.widthPixels / 2f, dm.heightPixels / 2f)
                }
            )
            SettingsRow(
                title = "刪除最後一點",
                showArrow = true,
                onClick = { SettingsRepository.removeLastPoint() }
            )
            SettingsRow(
                title = "重設為單點",
                showArrow = true,
                onClick = { SettingsRepository.clearPoints() }
            )
        }

        Spacer(Modifier.height(16.dp))
        if (status.lastError.isNotBlank()) {
            Text(status.lastError, color = AppColors.danger, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        ActionButton(
            label = if (status.running || status.replaying) "停止" else "開始",
            onClick = { ClickEngine.toggle() },
            danger = status.running || status.replaying
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ParamRow(title: String, value: String, suffix: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = AppColors.text(), fontSize = 16.sp, modifier = Modifier.weight(1f))
        NumberField(value, onChange, suffix)
    }
}
