package org.peercast.pecaplay.prefs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import org.peercast.pecaplay.PlayerLauncherSettings
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppTheme

/**

 */
class ViewerPrefsFragment : PreferenceFragment (), Preference.OnPreferenceChangeListener {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        addPreferencesFromResource(R.xml.pref_viewer)

        val version = PlayerLauncherSettings(activity).installedPlayerVersion
        val isInstalled = version != null

        with(findPreference("pref_viewer")) {
            icon = AppTheme(activity).getIcon(R.drawable.ic_ondemand_video_36dp)
            title = "PacaPlay Viewer"
            if (isInstalled) {
                summary = "Ver. $version"
            } else {
                setSummary(R.string.viewer_not_installed)
                val i = Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=org.peercast.pecaviewer"))
                intent = i
            }
        }

        with(findPreference("pref_viewer_wmv")) {
            isEnabled = isInstalled
            onPreferenceChangeListener = this@ViewerPrefsFragment
        }

        with(findPreference("pref_viewer_flv")) {
            isEnabled = isInstalled
            onPreferenceChangeListener = this@ViewerPrefsFragment
        }

    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val type = preference.key.substringAfterLast("_")
        PlayerLauncherSettings(activity).setEnabledType(type, newValue as Boolean)
        return true
    }
}
