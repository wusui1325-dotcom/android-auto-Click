package com.wusui.androidautoclick.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wusui.androidautoclick.BuildConfig
import com.wusui.androidautoclick.R
import com.wusui.androidautoclick.data.AppStore
import com.wusui.androidautoclick.engine.ClickBackend
import com.wusui.androidautoclick.engine.SessionType
import com.wusui.androidautoclick.i18n.AppLang
import com.wusui.androidautoclick.i18n.LocaleHelper
import com.wusui.androidautoclick.root.RootClickBackend
import com.wusui.androidautoclick.root.RootStatus
import com.wusui.androidautoclick.service.OverlayService
import com.wusui.androidautoclick.service.PermissionHelper
import com.wusui.androidautoclick.ui.components.SectionLabel
import com.wusui.androidautoclick.ui.components.SettingsCard
import com.wusui.androidautoclick.ui.components.SettingsRow

class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapBoot(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SettingsTheme {
                // language collected inside; recreate already applied new attachBaseContext
                HomeScreen()
            }
        }
        updateBarIcons()
    }

    override fun onResume() {
        super.onResume()
        updateBarIcons()
    }

    private fun updateBarIcons() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    var a11y by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var overlay by remember { mutableStateOf(PermissionHelper.canDrawOverlay(context)) }
    var pickType by remember { mutableStateOf(false) }
    val barVertical by AppStore.barVertical.collectAsState()
    val barSize by AppStore.barSize.collectAsState()
    val language by AppStore.language.collectAsState()
    val clickBackend by AppStore.clickBackend.collectAsState()
    val rootStatus by RootClickBackend.status.collectAsState()
    val rootBusy by RootClickBackend.busy.collectAsState()
    val ready = overlay && when (clickBackend) {
        ClickBackend.ACCESSIBILITY -> a11y
        ClickBackend.ROOT -> rootStatus == RootStatus.AUTHORIZED
    }
    val currentLang = AppLang.fromTag(language)
    val startSubtitle = when {
        !overlay && clickBackend == ClickBackend.ACCESSIBILITY && !a11y ->
            stringResource(R.string.start_need_perm)
        !overlay -> stringResource(R.string.start_need_overlay)
        clickBackend == ClickBackend.ACCESSIBILITY && !a11y ->
            stringResource(R.string.start_need_perm)
        clickBackend == ClickBackend.ROOT && rootStatus != RootStatus.AUTHORIZED ->
            stringResource(R.string.start_need_root)
        clickBackend == ClickBackend.ROOT -> stringResource(R.string.start_ready_root_sub)
        else -> stringResource(R.string.start_ready_sub)
    }
    val rootStatusLabel = when (rootStatus) {
        RootStatus.NOT_DETECTED -> stringResource(R.string.root_status_not_detected)
        RootStatus.NEED_GRANT -> stringResource(R.string.root_status_need_grant)
        RootStatus.AUTHORIZED -> stringResource(R.string.root_status_authorized)
        RootStatus.DENIED -> stringResource(R.string.root_status_denied)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                a11y = PermissionHelper.isAccessibilityEnabled(context)
                overlay = PermissionHelper.canDrawOverlay(context)
                RootClickBackend.refreshDetection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "AUTO-C",
            color = AppColors.text(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.version_line, BuildConfig.VERSION_NAME),
            color = AppColors.secondary(),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.section_permissions))
        SettingsCard {
            SettingsRow(
                title = stringResource(R.string.a11y_title),
                subtitle = when {
                    clickBackend == ClickBackend.ROOT && a11y ->
                        stringResource(R.string.a11y_on_root_mode)
                    clickBackend == ClickBackend.ROOT && !a11y ->
                        stringResource(R.string.a11y_off_root_mode)
                    a11y -> stringResource(R.string.a11y_on)
                    else -> stringResource(R.string.a11y_off_hint)
                },
                trailing = if (a11y) "✓" else stringResource(R.string.go_enable),
                showArrow = !a11y,
                onClick = { if (!a11y) PermissionHelper.openAccessibilitySettings(context) }
            )
            SettingsRow(
                title = stringResource(R.string.overlay_title),
                subtitle = if (overlay) stringResource(R.string.overlay_on) else stringResource(R.string.overlay_off_hint),
                trailing = if (overlay) "✓" else stringResource(R.string.go_enable),
                showArrow = !overlay,
                onClick = { if (!overlay) PermissionHelper.openOverlaySettings(context) }
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.section_click_method))
        SettingsCard {
            val a11ySelected = clickBackend == ClickBackend.ACCESSIBILITY
            SettingsRow(
                title = stringResource(R.string.click_method_a11y),
                subtitle = stringResource(R.string.click_method_a11y_sub),
                trailing = if (a11ySelected) "✓" else "",
                showArrow = !a11ySelected,
                onClick = { AppStore.setClickBackend(ClickBackend.ACCESSIBILITY) }
            )
            val rootSelected = clickBackend == ClickBackend.ROOT
            SettingsRow(
                title = stringResource(R.string.click_method_root),
                subtitle = stringResource(R.string.click_method_root_sub),
                trailing = when {
                    rootBusy -> "…"
                    rootSelected && rootStatus == RootStatus.AUTHORIZED -> "✓"
                    else -> rootStatusLabel
                },
                showArrow = !rootSelected || rootStatus != RootStatus.AUTHORIZED,
                onClick = {
                    AppStore.setClickBackend(ClickBackend.ROOT)
                    if (rootStatus != RootStatus.AUTHORIZED && !rootBusy) {
                        RootClickBackend.requestAccess(context)
                    }
                }
            )
            SettingsRow(
                title = stringResource(R.string.root_record_hint)
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.section_language))
        SettingsCard {
            AppLang.entries.forEach { lang ->
                val selected = lang == currentLang
                SettingsRow(
                    title = stringResource(lang.labelRes()),
                    trailing = if (selected) "✓" else "",
                    showArrow = !selected,
                    onClick = {
                        if (!selected) {
                            AppStore.setLanguage(lang.tag)
                            // AppCompat locales alone may not refresh Compose stringResource
                            (context as? Activity)?.recreate()
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.section_control_bar))
        SettingsCard {
            SettingsRow(
                title = stringResource(R.string.bar_orientation),
                subtitle = if (barVertical) stringResource(R.string.bar_vertical_now) else stringResource(R.string.bar_horizontal_now),
                trailing = if (barVertical) stringResource(R.string.bar_to_horizontal) else stringResource(R.string.bar_to_vertical),
                showArrow = true,
                onClick = { AppStore.setBarVertical(!barVertical) }
            )
            val sizeLabel = when (barSize) {
                0 -> stringResource(R.string.size_s)
                2 -> stringResource(R.string.size_l)
                else -> stringResource(R.string.size_m)
            }
            SettingsRow(
                title = stringResource(R.string.bar_size),
                subtitle = stringResource(R.string.bar_size_hint, sizeLabel),
                trailing = sizeLabel,
                showArrow = true,
                onClick = { AppStore.setBarSize((barSize + 1) % 3) }
            )
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.section_start))
        SettingsCard {
            if (OverlayService.isRunning) {
                SettingsRow(
                    title = stringResource(R.string.bar_running_title),
                    subtitle = stringResource(R.string.bar_running_sub),
                    trailing = stringResource(R.string.close),
                    showArrow = true,
                    onClick = {
                        context.startService(
                            Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE)
                        )
                    }
                )
            } else {
                SettingsRow(
                    title = stringResource(R.string.start_autoc_title),
                    subtitle = startSubtitle,
                    trailing = stringResource(R.string.start),
                    showArrow = true,
                    onClick = {
                        if (!ready) return@SettingsRow
                        pickType = true
                    }
                )
            }
        }

        if (pickType && ready && !OverlayService.isRunning) {
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.section_start_type))
            SettingsCard {
                SessionType.entries.forEach { type ->
                    SettingsRow(
                        title = stringResource(type.labelRes()),
                        subtitle = when (type) {
                            SessionType.SINGLE -> stringResource(R.string.session_single_sub)
                            SessionType.MULTI -> stringResource(R.string.session_multi_sub)
                            SessionType.RECORD -> stringResource(
                                if (clickBackend == ClickBackend.ROOT) R.string.session_record_sub_root
                                else R.string.session_record_sub
                            )
                        },
                        showArrow = true,
                        onClick = {
                            pickType = false
                            val i = Intent(context, OverlayService::class.java)
                                .putExtra(OverlayService.EXTRA_TYPE, type.name)
                            ContextCompat.startForegroundService(context, i)
                        }
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.cancel),
                    onClick = { pickType = false }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.section_help))
        SettingsCard {
            SettingsRow(
                title = stringResource(R.string.help_title),
                subtitle = stringResource(R.string.help_body)
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.section_about))
        SettingsCard {
            SettingsRow(
                title = stringResource(R.string.about_title),
                subtitle = stringResource(
                    R.string.about_body,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                )
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
