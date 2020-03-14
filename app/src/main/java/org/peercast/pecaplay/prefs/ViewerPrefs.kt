package org.peercast.pecaplay.prefs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppTheme

/**

 */
class ViewerPrefsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_viewer)

        val version = PecaPlayViewerSetting.installedVersion

        findPreference<Preference>("pref_viewer")!!.let {
            it.setIcon(R.drawable.ic_ondemand_video_36dp)
            it.icon.setTint(AppTheme.getIconColor(it.context))
            it.title = "PacaPlay Viewer"

            if (version != null) {
                it.summary = "Ver. $version"
            } else {
                it.setSummary(R.string.viewer_not_installed)
                val i = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?itemId=org.peercast.pecaviewer")
                )
                it.intent = i
            }
        }

        findPreference<Preference>("pref_viewer_wmv")!!.let {
            it.isEnabled = version != null
            it.setOnPreferenceChangeListener { _, newValue ->
                PecaPlayViewerSetting.setEnabled("WMV", newValue as Boolean)
                true
            }
        }

        findPreference<Preference>("pref_viewer_flv")!!.let {
            it.isEnabled = version != null
            it.setOnPreferenceChangeListener { _, newValue ->
                PecaPlayViewerSetting.setEnabled("FLV", newValue as Boolean)
                true
            }
        }

        findPreference<Preference>("pref_viewer_mkv")!!.let {
            it.isEnabled = version != null
            it.setOnPreferenceChangeListener { _, newValue ->
                PecaPlayViewerSetting.setEnabled("MKV", newValue as Boolean)
                true
            }
        }
    }

}
