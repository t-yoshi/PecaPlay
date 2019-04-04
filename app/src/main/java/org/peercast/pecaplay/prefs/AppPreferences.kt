package org.peercast.pecaplay.prefs


import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import androidx.core.content.edit
import org.peercast.pecaplay.yp4g.YpDisplayOrder

abstract class AppPreferences {
    /**夜間モードか */
    abstract val isNightMode: Boolean

    /**動作しているPeerCast。localhostまたは192.168.x.x*/
    abstract var peerCastUrl: Uri

    /**表示順*/
    abstract var displayOrder: YpDisplayOrder

    /**NGは非表示か、NGと表示するか*/
    abstract val isNgHidden: Boolean

    /**WMV,FLVなどのタイプでPecaPlayViewerが有効か*/
    abstract fun isViewerEnabled(type: String): Boolean

    /**通知するか*/
    abstract var isNotificationEnabled: Boolean

    /**通知音*/
    abstract var notificationSoundUrl: Uri?

    /**通知済み新着のChannel-Id*/
    abstract var notificationNewlyChannelsId: List<String>

    companion object {
        const val KEY_IS_NIGHT_MODE = "pref_is_night_mode"
        const val KEY_PEERCAST_SERVER_URL = "pref_peercast_server_url"
        const val KEY_NG_HIDDEN = "pref_ng_hidden"
    }

}

class DefaultAppPreferences(c: Context) : AppPreferences() {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(c)

    override val isNightMode: Boolean
        get() = prefs.getBoolean(KEY_IS_NIGHT_MODE, false)

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
        val type = type.toLowerCase()
        val default = type in listOf("wmv", "flv")//default true
        return prefs.getBoolean(KEY_PLAYER_ENABLED_PREFIX + type, default)
    }

    override var isNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_NOTIFICATION_ENABLED, value) }
        }

    override var notificationSoundUrl: Uri?
        set(value) {
            when (value) {
                null, Uri.EMPTY ->
                    prefs.edit { remove(KEY_NOTIFICATION_SOUND_URL) }
                else ->
                    prefs.edit { putString(KEY_NOTIFICATION_SOUND_URL, value.toString()) }
            }
        }
        get() = prefs.getString(KEY_NOTIFICATION_SOUND_URL, null)?.let(Uri::parse) ?: Uri.EMPTY

    override var notificationNewlyChannelsId: List<String>
        get() = prefs.getStringSet(KEY_NOTIFICATION_NEWLY_CHANNELS_ID, null)?.toList() ?: emptyList()
        set(value) {
            prefs.edit { putStringSet(KEY_NOTIFICATION_NEWLY_CHANNELS_ID, value.toSet()) }
        }


    companion object {
        private const val KEY_DISPLAY_ORDER = "pref_display_order"

        private const val KEY_PLAYER_ENABLED_PREFIX = "pref_viewer_" //(wmv|flv|mkf|...)

        private const val KEY_NOTIFICATION_ENABLED = "pref_notification_enabled"
        private const val KEY_NOTIFICATION_SOUND_URL = "pref_notification_sound_url"
        private const val KEY_NOTIFICATION_NEWLY_CHANNELS_ID = "pref_notification_newly_channels_id"

    }
}

