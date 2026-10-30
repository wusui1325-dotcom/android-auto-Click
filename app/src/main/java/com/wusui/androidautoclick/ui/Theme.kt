package com.wusui.androidautoclick.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Settings-style light black/white (個人中心風). */
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
    val danger = Color(0xFFC4A090)
    val trackBorderOff = Color(0xFF8E8E93)
    val trackBorderOn = Color(0xFFD1D1D6)
    val knob = Color.White

    @Composable fun isDark(): Boolean = false
    @Composable fun bg() = bgLight
    @Composable fun text() = textLight
    @Composable fun secondary() = secondaryLight
    @Composable fun divider() = dividerLight
    @Composable fun selected() = Color(0xFFE5E5EA)
    @Composable fun card() = cardLight
    @Composable fun cardBorder() = dividerLight
    @Composable fun circleBtn() = circleLight
    @Composable fun circleBtnPressed() = Color(0xFFD1D1D6)
    @Composable fun trackOff() = Color(0xFFE5E5EA)
    @Composable fun trackOn() = Color(0xFFD1D1D6)
}

@Composable
fun SettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = AppColors.bgLight,
            surface = AppColors.bgLight,
            onBackground = AppColors.textLight,
            onSurface = AppColors.textLight
        ),
        content = content
    )
}
