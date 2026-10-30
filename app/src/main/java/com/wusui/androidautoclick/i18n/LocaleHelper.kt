package com.wusui.androidautoclick.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.wusui.androidautoclick.R
import java.util.Locale

enum class AppLang(val tag: String) {
    ZH_HANT("zh-Hant"),
    ZH_HANS("zh-Hans"),
    EN("en");

    fun labelRes(): Int = when (this) {
        ZH_HANT -> R.string.lang_zh_hant
        ZH_HANS -> R.string.lang_zh_hans
        EN -> R.string.lang_en
    }

    companion object {
        fun fromTag(tag: String?): AppLang {
            val t = tag?.trim().orEmpty()
            return entries.firstOrNull { it.tag.equals(t, ignoreCase = true) }
                ?: when {
                    t.startsWith("zh-Hans", true) || t.equals("zh-CN", true) ||
                        t.equals("zh-SG", true) -> ZH_HANS
                    t.startsWith("en", true) -> EN
                    else -> ZH_HANT
                }
        }
    }
}

object LocaleHelper {

    fun apply(tag: String) {
        val lang = AppLang.fromTag(tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
    }

    /** Context whose resources match the persisted app language. */
    fun localized(base: Context): Context {
        val tag = try {
            com.wusui.androidautoclick.data.AppStore.language.value
        } catch (_: Exception) {
            AppLang.ZH_HANT.tag
        }
        val locale = Locale.forLanguageTag(AppLang.fromTag(tag).tag)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    fun str(base: Context, resId: Int): String = localized(base).getString(resId)

    fun str(base: Context, resId: Int, vararg args: Any): String =
        localized(base).getString(resId, *args)
}
