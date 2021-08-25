package org.peercast.pecaplay.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat


object AppTheme {
    @ColorInt
    fun getIconColor(c: Context): Int {
        val tv = TypedValue()
        c.theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
        return ResourcesCompat.getColor(c.resources, tv.resourceId, c.theme)
    }
}

