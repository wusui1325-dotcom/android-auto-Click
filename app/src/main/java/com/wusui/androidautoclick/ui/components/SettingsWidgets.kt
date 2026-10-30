package com.wusui.androidautoclick.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wusui.androidautoclick.ui.AppColors

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AppColors.secondary(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card())
            .border(1.dp, AppColors.cardBorder(), RoundedCornerShape(14.dp))
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .background(if (pressed) AppColors.selected() else AppColors.card())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = AppColors.text(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.01).em
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = AppColors.secondary(), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            Text(trailing, color = AppColors.secondary(), fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
        }
        if (showArrow) {
            Text("›", color = AppColors.arrow, fontSize = 20.sp)
        }
    }
}

@Composable
fun GraySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track = if (checked) AppColors.trackOn() else AppColors.trackOff()
    val border = if (checked) AppColors.trackBorderOn else AppColors.trackBorderOff
    Box(
        modifier = Modifier
            .size(46.dp, 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(track)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(AppColors.knob)
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.text(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = AppColors.secondary(), fontSize = 12.sp)
            }
        }
        GraySwitch(checked, onChange)
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    suffix: String = "",
    text: Boolean = false,
    fieldWidth: Int = 72
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (text) KeyboardType.Text else KeyboardType.Number
            ),
            textStyle = TextStyle(color = AppColors.text(), fontSize = 15.sp),
            cursorBrush = SolidColor(AppColors.text()),
            modifier = Modifier
                .width(fieldWidth.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (AppColors.isDark()) AppColors.circleDark else AppColors.circleLight)
                .border(1.dp, AppColors.divider(), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        if (suffix.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(suffix, color = AppColors.secondary(), fontSize = 13.sp)
        }
    }
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    danger && pressed -> AppColors.danger.copy(alpha = 0.28f)
                    danger -> AppColors.danger.copy(alpha = 0.16f)
                    pressed -> AppColors.selected()
                    else -> if (AppColors.isDark()) AppColors.circleDark else AppColors.circleLight
                }
            )
            .border(
                1.dp,
                if (danger) AppColors.danger.copy(alpha = 0.5f) else AppColors.arrow,
                RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (danger) AppColors.danger else AppColors.text(),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CircleBack(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (pressed) AppColors.circleBtnPressed() else AppColors.circleBtn())
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("‹", color = AppColors.text(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
