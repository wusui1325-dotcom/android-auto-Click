package com.wusui.androidautoclick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.wusui.androidautoclick.data.SettingsRepository

object AppColors {
    val bgDark = Color(0xFF000000)
    val bgLight = Color(0xFFF2F2F7)
    val textDark = Color(0xFFF5F5F7)
    val textLight = Color(0xFF1D1D1F)
    val secondaryDark = Color(0xFF8E8E93)
    val secondaryLight = Color(0xFF6E6E73)
    val dividerDark = Color(0xFF1C1C1E)
    val dividerLight = Color(0xFFD1D1D6)
    val cardDark = Color(0xFF111111)
    val cardLight = Color.White
    val arrow = Color(0xFF636366)
    val circleDark = Color(0xFF1C1C1E)
    val circleLight = Color(0xFFE5E5EA)
    val trackOffDark = Color(0xFF2C2C2E)
    val trackOnDark = Color(0xFF3A3A3C)
    val trackBorderOff = Color(0xFF8E8E93)
    val trackBorderOn = Color(0xFFD1D1D6)
    val knob = Color.White
    val danger = Color(0xFFC4A090)

    @Composable
    fun isDark(): Boolean {
        val cfg by SettingsRepository.config.collectAsState()
        return cfg.darkTheme
    }

    @Composable fun bg() = if (isDark()) bgDark else bgLight
    @Composable fun text() = if (isDark()) textDark else textLight
    @Composable fun secondary() = if (isDark()) secondaryDark else secondaryLight
    @Composable fun divider() = if (isDark()) dividerDark else dividerLight
    @Composable fun selected() = if (isDark()) dividerDark else Color(0xFFE5E5EA)
    @Composable fun card() = if (isDark()) cardDark else cardLight
    @Composable fun cardBorder() = if (isDark()) dividerDark else dividerLight
    @Composable fun circleBtn() = if (isDark()) circleDark else circleLight
    @Composable fun circleBtnPressed() = if (isDark()) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)
    @Composable fun trackOff() = if (isDark()) trackOffDark else Color(0xFFE5E5EA)
    @Composable fun trackOn() = if (isDark()) trackOnDark else Color(0xFFD1D1D6)
}

@Composable
fun SettingsTheme(content: @Composable () -> Unit) {
    val dark = AppColors.isDark()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            background = AppColors.bgDark,
            surface = AppColors.bgDark,
            onBackground = AppColors.textDark,
            onSurface = AppColors.textDark
        ) else lightColorScheme(
            background = AppColors.bgLight,
            surface = AppColors.bgLight,
            onBackground = AppColors.textLight,
            onSurface = AppColors.textLight
        ),
        content = content
    )
}
