package org.peercast.pecaplay.app

import android.app.UiModeManager
import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat


object AppTheme {

    fun initNightMode(c: Context, isNightMode: Boolean) {
        val uiMan = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (isNightMode) {
            uiMan.nightMode = UiModeManager.MODE_NIGHT_YES
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            uiMan.nightMode = UiModeManager.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    @ColorInt
    fun getIconColor(c: Context): Int {
        val tv = TypedValue()
        c.theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
        return ResourcesCompat.getColor(c.resources, tv.resourceId, c.theme)
    }
}

