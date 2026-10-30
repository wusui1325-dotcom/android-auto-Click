package com.wusui.androidautoclick

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.wusui.androidautoclick.service.ClickAccessibilityService

object PermissionHelper {

    /** 檢查無障礙服務是否已啟用 */
    fun isAccessibilityEnabled(context: Context): Boolean {
        if (ClickAccessibilityService.isEnabled()) return true

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val serviceName = "${context.packageName}/${ClickAccessibilityService::class.java.name}"
        return enabledServices.split(':').any { it.equals(serviceName, ignoreCase = true) }
    }

    /** 跳轉至系統無障礙設定頁 */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** 檢查是否已取得懸浮窗權限 */
    fun canDrawOverlay(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /** 跳轉至懸浮窗權限設定頁 */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
