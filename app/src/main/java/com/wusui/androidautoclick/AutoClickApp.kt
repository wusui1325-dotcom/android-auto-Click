package com.wusui.androidautoclick

import android.app.Application
import com.wusui.androidautoclick.data.AppStore
import com.wusui.androidautoclick.service.ClickNotification

class AutoClickApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStore.init(this)
        ClickNotification.ensureChannel(this)
    }
}
