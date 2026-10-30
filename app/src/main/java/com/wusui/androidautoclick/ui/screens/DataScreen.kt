package com.wusui.androidautoclick.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.ui.AppColors
import com.wusui.androidautoclick.ui.components.ActionButton
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow

@Composable
fun DataScreen() {
    val stats by SettingsRepository.stats.collectAsState()
    val context = LocalContext.current
    var importText by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("執行統計")
        SettingsCard {
            SettingsRow("累計動作", trailing = "${stats.lifetimeActions}")
            SettingsRow("累計場次", trailing = "${stats.lifetimeSessions}")
            SettingsRow("上一場", trailing = stats.lastSessionLabel())
            SettingsRow(
                title = "重置統計",
                showArrow = true,
                onClick = {
                    SettingsRepository.resetStats()
                    feedback = "統計已重置"
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("匯出／匯入")
        SettingsCard {
            SettingsRow(
                title = "複製設定到剪貼簿",
                subtitle = "含參數、標記點、目前序列與已存序列",
                showArrow = true,
                onClick = {
                    val json = SettingsRepository.exportBundle()
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("AutoClick", json))
                    feedback = "已複製到剪貼簿"
                }
            )
            SettingsRow(
                title = "分享匯出檔",
                showArrow = true,
                onClick = {
                    val json = SettingsRepository.exportBundle()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "AutoClick 設定")
                        putExtra(Intent.EXTRA_TEXT, json)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(send, "分享 AutoClick 設定"))
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("貼上匯入")
        BasicTextField(
            value = importText,
            onValueChange = { importText = it },
            textStyle = TextStyle(color = AppColors.text(), fontSize = 13.sp),
            cursorBrush = SolidColor(AppColors.text()),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.card())
                .border(1.dp, AppColors.cardBorder(), RoundedCornerShape(14.dp))
                .padding(14.dp)
        )
        Spacer(Modifier.height(10.dp))
        ActionButton(
            label = "匯入貼上的內容",
            onClick = {
                val err = SettingsRepository.importBundle(importText)
                feedback = if (err == null) "匯入成功" else "失敗：$err"
            }
        )

        if (feedback.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(feedback, color = AppColors.secondary(), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "匯出為 JSON，可備份到備忘錄或傳給另一台手機。不包含金鑰／帳號。",
            color = AppColors.secondary(),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}
