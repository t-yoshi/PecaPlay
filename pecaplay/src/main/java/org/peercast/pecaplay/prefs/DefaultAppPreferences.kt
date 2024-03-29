package org.peercast.pecaplay.prefs

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.peercast.pecaplay.yp4g.YpDisplayOrder

class DefaultAppPreferences(c: Context) : AppPreferences() {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(c)

    override var peerCastUrl: Uri
        set(value) {
            prefs.edit { putString(KEY_PEERCAST_SERVER_URL, value.toString()) }
        }
        get() {
            val s = prefs.getString(KEY_PEERCAST_SERVER_URL, "")
            if (s != "")
                return Uri.parse(s)
            return Uri.parse("http://localhost:7144/").also {
                prefs.edit { putString(KEY_PEERCAST_SERVER_URL, it.toString()) }
            }
        }

    override var displayOrder: YpDisplayOrder
        set(value) = prefs.edit { putString(KEY_DISPLAY_ORDER, value.name) }
        get() = YpDisplayOrder.fromName(prefs.getString(KEY_DISPLAY_ORDER, null))

    override val isNgHidden: Boolean
        get() = prefs.getBoolean(KEY_NG_HIDDEN, false)


    override fun isViewerEnabled(type: String): Boolean {
        return when (type.lowercase()) {
            "flv", "mkv", "webm" -> prefs.getBoolean(KEY_PLAYER_ENABLED, true)
            else -> false
        }
    }

    override var isNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_NOTIFICATION_ENABLED, value) }
        }


    companion object {
        const val KEY_PEERCAST_SERVER_URL = "pref_peercast_server_url"
        const val KEY_NG_HIDDEN = "pref_ng_hidden"

        private const val KEY_DISPLAY_ORDER = "pref_display_order"

        private const val KEY_PLAYER_ENABLED = "pref_viewer_enabled"

        private const val KEY_NOTIFICATION_ENABLED = "pref_notification_enabled"

    }
}