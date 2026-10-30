package com.wusui.androidautoclick

import android.app.Application
import android.content.Context
import com.wusui.androidautoclick.data.AppStore
import com.wusui.androidautoclick.i18n.LocaleHelper
import com.wusui.androidautoclick.service.ClickNotification

class AutoClickApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapBoot(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppStore.init(this)
        LocaleHelper.apply(AppStore.language.value)
        ClickNotification.ensureChannel(this)
    }
}
