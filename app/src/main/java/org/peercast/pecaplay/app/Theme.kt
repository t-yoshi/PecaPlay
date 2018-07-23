package org.peercast.pecaplay.app

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.ColorStateList
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.util.TypedValue
import org.peercast.pecaplay.prefs.AppPreferences
import timber.log.Timber

object AppNightMode {

    fun init(a: Application) {
        val mode = AppPreferences(a).nightMode
        setMode(a, mode)
    }

    fun setMode(c: Context, mode: String) {
        val manager = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        manager.nightMode = when (mode.toLowerCase()) {
            "daylight" -> UiModeManager.MODE_NIGHT_NO
            "night" -> UiModeManager.MODE_NIGHT_YES
            "auto" -> UiModeManager.MODE_NIGHT_AUTO
            else -> UiModeManager.MODE_NIGHT_YES
        }
        Timber.i("set night mode: $mode")
    }

    val MODES = listOf("daylight", "night", "auto")
}


class AppTheme(val context: Context) {
    val theme = context.theme!!
    val resource = context.resources!!

    private fun resolveAttribute(@AttrRes attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.resourceId
    }

    @ColorInt
    private fun resolveColor(@AttrRes attr: Int): Int {
        return context.getColor(resolveAttribute(attr))
    }

    val textColorPrimary: Int
        @ColorInt get() = resolveColor(android.R.attr.textColorPrimary)

    val textColorSecondary: Int
        @ColorInt get() = resolveColor(android.R.attr.textColorSecondary)

    val actionBarIconTint: ColorStateList
        get() = resource.getColorStateList(
                resolveAttribute(android.R.attr.textColorPrimary),
                theme)!!

    val iconTint: ColorStateList
        get() = resource.getColorStateList(
                resolveAttribute(android.R.attr.textColorSecondary),
                theme)!!

    fun getActionBarIcon(@DrawableRes icon: Int) =
            resource.getDrawable(icon, theme).apply {
                setTint(textColorPrimary)
            }

    fun getIcon(@DrawableRes icon: Int) =
            resource.getDrawable(icon, theme).apply {
                setTint(textColorSecondary)
            }!!
}

