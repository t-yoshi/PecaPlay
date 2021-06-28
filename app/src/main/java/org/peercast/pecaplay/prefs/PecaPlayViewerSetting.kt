package org.peercast.pecaplay.prefs

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.*

object PecaPlayViewerSetting : KoinComponent {
    private val a by inject<Application>()
    private val appPrefs by inject<AppPreferences>()

    fun setEnabled(type: String, enabled: Boolean) {
        val aliasName = "org.peercast.pecaplay.PecaPlayViewer_${type.uppercase(Locale.getDefault())}"
        val name = ComponentName(a.packageName, aliasName)
        val state = if (enabled)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        Timber.i("Update ComponentEnabledSetting($aliasName, isEnabled=$enabled")

        a.packageManager.setComponentEnabledSetting(
            name, state, PackageManager.DONT_KILL_APP
        )
    }

    val installedVersion: String?
        get() {
            try {
                val info = a.packageManager.getPackageInfo("org.peercast.pecaviewer", 0)
                return info.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w(e)
                return null
            }
        }

    val enabledTypes: List<String>
        get() {
            if (installedVersion == null)
                return emptyList()
            return TYPES.filter(appPrefs::isViewerEnabled)
        }

    fun initComponentSetting() {
        val installed = installedVersion != null
        for (type in TYPES) {
            val enabled = appPrefs.isViewerEnabled(type)
            setEnabled(
                type,
                installed && enabled
            )
        }
    }

    private val TYPES = arrayOf("WMV", "FLV", "MKV")

}