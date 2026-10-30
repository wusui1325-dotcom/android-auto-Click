package com.wusui.androidautoclick.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel("必要權限")
        SettingsCard {
            SettingsRow(
                title = "無障礙服務",
                subtitle = "自動點擊／手勢的核心能力。必須手動開啟。",
                trailing = if (a11y) "已開" else "去開啟",
                showArrow = true,
                onClick = { PermissionHelper.openAccessibilitySettings(context) }
            )
            SettingsRow(
                title = "懸浮窗",
                subtitle = "顯示浮動控制與標記點。",
                trailing = if (overlay) "已開" else "去開啟",
                showArrow = true,
                onClick = { PermissionHelper.openOverlaySettings(context) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "未開啟無障礙時，開始會顯示明確錯誤，不會 silently 失敗。",
            color = AppColors.secondary(),
            fontSize = 12.sp
        )
    }
}
