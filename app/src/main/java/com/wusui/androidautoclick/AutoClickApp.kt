package com.wusui.androidautoclick

import android.app.Application
import com.wusui.androidautoclick.data.SettingsRepository
import com.wusui.androidautoclick.service.ClickNotification

class AutoClickApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepository.init(this)
        ClickNotification.ensureChannel(this)
    }
}
