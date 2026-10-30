package com.wusui.androidautoclick.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.engine.ClickEngine
import com.wusui.androidautoclick.engine.ClickMode
import com.wusui.androidautoclick.engine.SequenceStep
import com.wusui.androidautoclick.ui.AppColors
import com.wusui.androidautoclick.ui.components.ActionButton
import com.wusui.androidautoclick.ui.components.NumberField
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow

@Composable
fun SequenceScreen() {
    val steps by SettingsRepository.sequenceSteps.collectAsState()
    val saved by SettingsRepository.savedSequences.collectAsState()
    val cfg by SettingsRepository.config.collectAsState()
    val status by ClickEngine.status.collectAsState()
    val context = LocalContext.current
    var saveName by remember { mutableStateOf("預設") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("錄製")
        SettingsCard {
            SettingsRow(
                title = if (status.recording) "停止錄製" else "開始錄製",
                subtitle = "監聽無障礙點擊事件，步間延遲用真實時間戳。遊戲自繪 View 通常錄不到。",
                showArrow = true,
                onClick = {
                    if (status.recording) ClickEngine.stopRecording()
                    else ClickEngine.startRecording(clear = true)
                }
            )
            SettingsRow(
                title = "手動加中央點擊",
                showArrow = true,
                onClick = {
                    val dm = context.resources.displayMetrics
                    if (!status.recording) ClickEngine.startRecording(clear = false)
                    ClickEngine.recordClick(dm.widthPixels / 2f, dm.heightPixels / 2f)
                }
            )
            SettingsRow(
                title = "加等待 500ms",
                showArrow = true,
                onClick = {
                    if (!status.recording) ClickEngine.startRecording(clear = false)
                    ClickEngine.recordWait(500)
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "限制：僅能錄到標準無障礙節點點擊；遊戲／自繪畫面請用懸浮標記手動建序列。",
            color = AppColors.secondary(),
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))
        SectionLabel("回放")
        SettingsCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("循環次數", color = AppColors.text(), fontSize = 16.sp, modifier = Modifier.weight(1f))
                NumberField(
                    value = if (cfg.sequenceLoops == 0) "" else cfg.sequenceLoops.toString(),
                    onChange = { v ->
                        val n = if (v.isBlank()) 0 else v.toIntOrNull() ?: return@NumberField
                        SettingsRepository.updateConfig { it.copy(sequenceLoops = n.coerceAtLeast(0)) }
                    },
                    suffix = "空=無限"
                )
            }
            SettingsRow(
                title = "設為序列模式並回放",
                showArrow = true,
                onClick = {
                    SettingsRepository.updateConfig { it.copy(mode = ClickMode.SEQUENCE) }
                    ClickEngine.start()
                }
            )
            if (status.replaying) {
                SettingsRow(
                    title = "停止回放",
                    showArrow = true,
                    onClick = { ClickEngine.stop() }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("步驟（${steps.size}）— 視覺編輯")
        SettingsCard {
            if (steps.isEmpty()) {
                SettingsRow("尚無步驟", subtitle = "錄製或手動新增")
            }
            steps.forEachIndexed { i, step ->
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(step.summary(), color = AppColors.text(), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("延遲", color = AppColors.secondary(), fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        NumberField(step.delayMs.toString(), { v ->
                            v.toLongOrNull()?.let { d ->
                                SettingsRepository.updateSequenceStep(i, step.copy(delayMs = d))
                            }
                        }, "ms")
                        Spacer(Modifier.weight(1f))
                        TinyAction("↑") { SettingsRepository.moveSequenceStep(i, (i - 1).coerceAtLeast(0)) }
                        TinyAction("↓") { SettingsRepository.moveSequenceStep(i, (i + 1).coerceAtMost(steps.lastIndex)) }
                        TinyAction("刪") { SettingsRepository.removeSequenceStep(i) }
                    }
                }
            }
            SettingsRow(
                title = "清空步驟",
                showArrow = true,
                onClick = { SettingsRepository.clearSequenceSteps() }
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("已存序列")
        SettingsCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("名稱", color = AppColors.text(), fontSize = 16.sp, modifier = Modifier.weight(1f))
                NumberField(saveName, { saveName = it }, "")
            }
            SettingsRow(
                title = "儲存目前步驟",
                showArrow = true,
                onClick = { SettingsRepository.saveSequenceAs(saveName.ifBlank { "未命名" }) }
            )
            saved.keys.sorted().forEach { name ->
                SettingsRow(
                    title = name,
                    subtitle = "${saved[name]?.size ?: 0} 步 · 點右側載入",
                    trailing = "載入",
                    showArrow = true,
                    onClick = { SettingsRepository.loadSequence(name) }
                )
                SettingsRow(
                    title = "刪除「$name」",
                    onClick = { SettingsRepository.deleteSavedSequence(name) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TinyAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AppColors.secondary(),
        fontSize = 13.sp,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick)
    )
}
