package org.peercast.pecaplay.app

import android.app.UiModeManager
import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat


object AppTheme {

    fun initNightMode(c: Context, isNightMode:  Boolean){
        val uiMan = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (isNightMode){
            uiMan.nightMode = UiModeManager.MODE_NIGHT_YES
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            uiMan.nightMode = UiModeManager.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
/*

    private val nightModes = mutableListOf(
        "Night" to UiModeManager.MODE_NIGHT_YES,
        "Daylight" to UiModeManager.MODE_NIGHT_NO
    ).also {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            it += "Auto" to UiModeManager.MODE_NIGHT_AUTO
    }.toMap()


    fun setNightMode(c: Context, mode: String) {
        val manager = c.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val nmode = nightModes[mode] ?: kotlin.run {
            Timber.w("Invalid night mode: $mode")
            UiModeManager.MODE_NIGHT_YES
        }
        //Timber.i("set night mode: $mode[$nmode]")

        //Android6から動的にナイトモードを切り替えられる
        manager.nightMode = nmode

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            AppCompatDelegate.setDefaultNightMode(nmode)
        }
    }

    /**Android5でナイトモードを適用にするにはActivity.onCreate前に呼ぶ。 */
    fun applyLocalNightModeLollipop(a: AppCompatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //Timber.d("nmode=${AppCompatDelegate.getDefaultNightMode()}")
            a.delegate.setLocalNightMode(AppCompatDelegate.getDefaultNightMode())
        }
    }*/

    @ColorInt
    fun getIconColor(c: Context): Int {
        val tv = TypedValue()
        c.theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
        return ResourcesCompat.getColor(c.resources, tv.resourceId, c.theme)
    }
}

