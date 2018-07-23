package org.peercast.pecaplay.prefs


import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.preference.PreferenceManager
import org.peercast.pecaplay.yp4g.YpOrder

class AppPreferences(c: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(c)

    val nightMode: String
        get() = prefs.getString(KEY_NIGHT_MODE, "?")

    var peerCastUrl: Uri
        set(value) {
            prefs.edit().putString(KEY_PEERCAST_URL, value.toString()).apply()
        }
        get() {
            val s = prefs.getString(KEY_PEERCAST_URL, "")
            if (s != "")
                return Uri.parse(s)
            return Uri.parse("http://localhost:7144/").also {
                peerCastUrl = it
            }
        }

    /**
     * この「お気に入り」が有効かどうか。
     */
    @Deprecated("PecaPlay < v5")
    fun isYellowPageEnabled(ypName: String): Boolean {
        return prefs.getBoolean(getYellowPageKey(ypName), false)
    }

    @Deprecated("PecaPlay < v5")
    fun isFavoriteEnabled(name: String): Boolean {
        val k = KEY_FAVORITE_ENABLED_PREFIX + name
        return prefs.getBoolean(k, false)
    }

    var displayOrder: YpOrder
        set(value) = prefs.edit().putString(KEY_DISPLAY_ORDER, value.name).apply()
        get() = YpOrder.fromName(prefs.getString(KEY_DISPLAY_ORDER, ""))

    val isNgHidden: Boolean
        get() = prefs.getBoolean(KEY_NG_HIDDEN, false)


    fun isViewerEnabled(type: String): Boolean {
        val type = type.toLowerCase()
        val default = type in arrayOf("wmv", "flv")
        return prefs.getBoolean(KEY_PLAYER_ENABLED_PREFIX + type, default)
    }

    val isAppDebugMode: Boolean
        get() = prefs.getBoolean(KEY_APP_DEBUG_MODE, false)

    var isNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()
        }

//    val isAppNotificationEnabled: Boolean
//        get() = prefs.getBoolean(KEY_APP_NOTIFICATION_ENABLED, false)
//
//    fun putAppNotificationEnabled(b: Boolean) {
//        prefs.edit().putBoolean(KEY_APP_NOTIFICATION_ENABLED, b).apply()
//    }

//    val notificationTypes: Set<NotificationType>
//        get() {
//            val types = NotificationType.values()
//            return prefs.getStringSet(KEY_NOTIFICATION_TYPES, emptySet())
//                    .mapNotNull { n ->
//                        types.firstOrNull { it.name == n }
//                    }
//                    .toSet()
//        }


    var notificationSoundUrl: Uri?
        set(value) {
            prefs.edit().putString(KEY_NOTIFICATION_SOUND_URL, value?.toString()).apply()
        }
        get() {
            return prefs.getString(KEY_NOTIFICATION_SOUND_URL, null)?.let {
                Uri.parse(it)
            }
        }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val TAG = "AppPreferences"
        const val KEY_NIGHT_MODE = "pref_night_mode"

        private const val KEY_PEERCAST_URL = "pref_peercast_server_url"
        private const val KEY_DISPLAY_ORDER = "pref_display_order"

        @Deprecated("PecaPlay < v5")
        private const val KEY_YP_ENABLED_PREFIX = "pref_yellowpage_enabled_"
        @Deprecated("PecaPlay < v5")
        private const val KEY_FAVORITE_ENABLED_PREFIX = "pref_favorite_enabled_"

        private const val KEY_NG_HIDDEN = "pref_ng_hidden"

        private const val KEY_PLAYER_ENABLED_PREFIX = "pref_viewer_" //(wmv|flv|mkf|...)
        private const val KEY_APP_DEBUG_MODE = "pref_app_debug_mode"

        private const val KEY_NOTIFICATION_ENABLED = "pref_notification_enabled"
        private const val KEY_NOTIFICATION_SOUND_URL = "pref_notification_sound_url"

        @Deprecated("PecaPlay < v5")
        fun getYellowPageKey(ypName: String): String {
            if (ypName.isEmpty()) throw IllegalArgumentException("ypName is empty")
            return KEY_YP_ENABLED_PREFIX + ypName
        }
    }


}
