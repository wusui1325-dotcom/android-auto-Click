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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.ui.AppColors
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow
import com.wusui.androidautoclick.ui.components.SwitchRow

@Composable
fun AboutScreen(onThemeChange: () -> Unit) {
    val cfg by SettingsRepository.config.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("外觀")
        SettingsCard {
            SwitchRow(
                title = "深色模式",
                checked = cfg.darkTheme,
                onChange = {
                    SettingsRepository.updateConfig { c -> c.copy(darkTheme = it) }
                    onThemeChange()
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("關於")
        SettingsCard {
            SettingsRow("版本", trailing = "2.0.1")
            SettingsRow("架構", trailing = "engine / data / service / ui")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "免廣告 · 黑白 Settings 風 · 手勢嚴格串行＋取消軟重試。不收集資料。",
            color = AppColors.secondary(),
            fontSize = 12.sp
        )
    }
}
