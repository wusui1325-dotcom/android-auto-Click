package com.wusui.androidautoclick.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.wusui.androidautoclick.R
import java.util.Locale

enum class AppLang(val tag: String) {
    ZH_HANT("zh-TW"),
    ZH_HANS("zh-CN"),
    EN("en");

    fun toLocale(): Locale = when (this) {
        ZH_HANT -> Locale.TRADITIONAL_CHINESE // zh-TW
        ZH_HANS -> Locale.SIMPLIFIED_CHINESE  // zh-CN
        EN -> Locale.ENGLISH
    }

    fun labelRes(): Int = when (this) {
        ZH_HANT -> R.string.lang_zh_hant
        ZH_HANS -> R.string.lang_zh_hans
        EN -> R.string.lang_en
    }

    companion object {
        fun fromTag(tag: String?): AppLang {
            val t = tag?.trim().orEmpty()
            return when {
                t.equals("zh-TW", true) || t.equals("zh-HK", true) || t.equals("zh-MO", true) ||
                    t.equals("zh-Hant", true) || t.startsWith("zh-Hant", true) -> ZH_HANT
                t.equals("zh-CN", true) || t.equals("zh-SG", true) ||
                    t.equals("zh-Hans", true) || t.startsWith("zh-Hans", true) -> ZH_HANS
                t.startsWith("en", true) -> EN
                t.equals("zh", true) -> ZH_HANT
                else -> entries.firstOrNull { it.tag.equals(t, ignoreCase = true) } ?: ZH_HANT
            }
        }
    }
}

object LocaleHelper {

    private const val PREF = "autoclick_v3"
    private const val KEY = "appLanguage"

    fun readPersistedTag(base: Context): String {
        val raw = base.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return AppLang.fromTag(raw).tag
    }

    fun apply(tag: String) {
        val lang = AppLang.fromTag(tag)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
    }

    /** Wrap before AppStore.init (Application / Activity attachBaseContext). */
    fun wrapBoot(base: Context): Context = wrapWithTag(base, readPersistedTag(base))

    fun wrapWithTag(base: Context, tag: String): Context {
        val lang = AppLang.fromTag(tag)
        val locale = lang.toLocale()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Context whose resources match the persisted app language. */
    fun localized(base: Context): Context {
        val tag = try {
            com.wusui.androidautoclick.data.AppStore.language.value
        } catch (_: Exception) {
            readPersistedTag(base)
        }
        return wrapWithTag(base, tag)
    }

    fun str(base: Context, resId: Int): String = localized(base).getString(resId)

    fun str(base: Context, resId: Int, vararg args: Any): String =
        localized(base).getString(resId, *args)
}
