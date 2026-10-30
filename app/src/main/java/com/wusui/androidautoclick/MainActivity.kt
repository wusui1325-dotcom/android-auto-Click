package com.wusui.androidautoclick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoClickTheme {
                ShellScreen()
            }
        }
    }
}

@Composable
fun AutoClickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1A73E8),
            onPrimary = Color.White,
            surface = Color(0xFFF8F9FA),
            onSurface = Color(0xFF202124)
        ),
        content = content
    )
}

@Composable
fun ShellScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var overlayEnabled by remember { mutableStateOf(PermissionHelper.canDrawOverlay(context)) }

    // 回到前台時重新檢查權限狀態
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = PermissionHelper.isAccessibilityEnabled(context)
                overlayEnabled = PermissionHelper.canDrawOverlay(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text = "AutoClick",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A73E8)
            )
            Text(
                text = "極簡 · 免廣告 · 精準點擊",
                fontSize = 14.sp,
                color = Color(0xFF5F6368)
            )

            Spacer(Modifier.height(24.dp))

            PermissionCard(
                title = "無障礙服務",
                description = "執行自動點擊的核心權限，必須手動開啟",
                enabled = accessibilityEnabled,
                onRequest = { PermissionHelper.openAccessibilitySettings(context) }
            )

            PermissionCard(
                title = "懸浮窗",
                description = "用於顯示浮動控制面板（後續功能）",
                enabled = overlayEnabled,
                onRequest = { PermissionHelper.openOverlaySettings(context) }
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "殼階段 · 權限已鋪墊完成",
                fontSize = 12.sp,
                color = Color(0xFF9AA0A6)
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    enabled: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(description, fontSize = 13.sp, color = Color(0xFF5F6368))
            }
            if (enabled) {
                Text("已開啟", color = Color(0xFF34A853), fontWeight = FontWeight.Medium)
            } else {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                ) {
                    Text("去開啟")
                }
            }
        }
    }
}
