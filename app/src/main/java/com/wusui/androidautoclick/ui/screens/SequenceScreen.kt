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
import com.wusui.androidautoclick.ui.components.NumberField
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow

@Composable
fun SequenceScreen() {
    val steps by SettingsRepository.sequenceSteps.collectAsState()
    val saved by SettingsRepository.savedSequences.collectAsState()
    val points by SettingsRepository.points.collectAsState()
    val cfg by SettingsRepository.config.collectAsState()
    val status by ClickEngine.status.collectAsState()
    val context = LocalContext.current
    var saveName by remember { mutableStateOf("預設") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (status.lastError.isNotBlank()) {
            Text(status.lastError, color = AppColors.danger, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        SectionLabel("手動建步（遊戲／自繪推薦）")
        SettingsCard {
            SettingsRow(
                title = "標記點 → 序列步驟",
                subtitle = "把目前 ${points.size} 個懸浮標記匯成點擊步（間距 300ms）",
                showArrow = true,
                onClick = { ClickEngine.importPointsAsSequence(300) }
            )
            SettingsRow(
                title = "加中央點擊步",
                subtitle = if (status.recording) "錄製中追加" else "自動進入錄製（不清空）",
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
            if (points.size >= 2) {
                SettingsRow(
                    title = "加滑動步（點1→點2）",
                    showArrow = true,
                    onClick = {
                        if (!status.recording) ClickEngine.startRecording(clear = false)
                        val a = points[0]; val b = points[1]
                        ClickEngine.recordSwipe(a.x, a.y, b.x, b.y)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("無障礙錄製")
        SettingsCard {
            SettingsRow(
                title = if (status.recording) "停止錄製並儲存" else "開始錄製（清空重來）",
                subtitle = "監聽 TYPE_VIEW_CLICKED；遊戲自繪通常錄不到。",
                showArrow = true,
                onClick = {
                    if (status.recording) ClickEngine.stopRecording()
                    else ClickEngine.startRecording(clear = true)
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "系統限制：遊戲／自繪畫面錄不到節點點擊。請用懸浮標記拖到目標後「標記→序列步」。",
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
                title = if (status.replaying) "停止回放" else "設為序列模式並回放",
                showArrow = true,
                onClick = {
                    if (status.replaying) ClickEngine.stop()
                    else {
                        SettingsRepository.updateConfig { it.copy(mode = ClickMode.SEQUENCE) }
                        ClickEngine.start()
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("步驟（${steps.size}）— 視覺編輯")
        SettingsCard {
            if (steps.isEmpty()) {
                SettingsRow("尚無步驟", subtitle = "錄製或用標記手動建步")
            }
            steps.forEachIndexed { i, step ->
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("${i + 1}. ${step.summary()}", color = AppColors.text(), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("延遲", color = AppColors.secondary(), fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        NumberField(step.delayMs.toString(), { v ->
                            v.toLongOrNull()?.let { d ->
                                SettingsRepository.updateSequenceStep(i, step.copy(delayMs = d.coerceAtLeast(0)))
                            }
                        }, "ms")
                        Spacer(Modifier.weight(1f))
                        TinyAction("↑") { SettingsRepository.moveSequenceStep(i, (i - 1).coerceAtLeast(0)) }
                        TinyAction("↓") { SettingsRepository.moveSequenceStep(i, (i + 1).coerceAtMost(steps.lastIndex)) }
                        TinyAction("複") { SettingsRepository.duplicateSequenceStep(i) }
                        TinyAction("刪") { SettingsRepository.removeSequenceStep(i) }
                    }
                    if (step.type == SequenceStep.StepType.CLICK || step.type == SequenceStep.StepType.SWIPE) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("X", color = AppColors.secondary(), fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            NumberField(step.x.toInt().toString(), { v ->
                                v.toFloatOrNull()?.let { x ->
                                    SettingsRepository.updateSequenceStep(i, step.copy(x = x))
                                }
                            }, fieldWidth = 56)
                            Spacer(Modifier.width(8.dp))
                            Text("Y", color = AppColors.secondary(), fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            NumberField(step.y.toInt().toString(), { v ->
                                v.toFloatOrNull()?.let { y ->
                                    SettingsRepository.updateSequenceStep(i, step.copy(y = y))
                                }
                            }, fieldWidth = 56)
                            if (step.type == SequenceStep.StepType.SWIPE) {
                                Spacer(Modifier.width(8.dp))
                                Text("→", color = AppColors.secondary(), fontSize = 12.sp)
                                NumberField(step.x2.toInt().toString(), { v ->
                                    v.toFloatOrNull()?.let { x2 ->
                                        SettingsRepository.updateSequenceStep(i, step.copy(x2 = x2))
                                    }
                                }, fieldWidth = 56)
                                NumberField(step.y2.toInt().toString(), { v ->
                                    v.toFloatOrNull()?.let { y2 ->
                                        SettingsRepository.updateSequenceStep(i, step.copy(y2 = y2))
                                    }
                                }, fieldWidth = 56)
                            }
                        }
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
                NumberField(saveName, { saveName = it }, text = true, fieldWidth = 120)
            }
            SettingsRow(
                title = "儲存目前步驟",
                showArrow = true,
                onClick = { SettingsRepository.saveSequenceAs(saveName.ifBlank { "未命名" }) }
            )
            saved.keys.sorted().forEach { name ->
                SettingsRow(
                    title = name,
                    subtitle = "${saved[name]?.size ?: 0} 步",
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
