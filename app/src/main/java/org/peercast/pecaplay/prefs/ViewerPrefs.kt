package org.peercast.pecaplay.prefs

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppTheme

/**

 */
class ViewerPrefsFragment : PreferenceFragmentCompat() {


    private fun getInstalledViewerVersion(): String? {
        return try {
            val info = context!!.packageManager.getPackageInfo("org.peercast.pecaviewer", 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    //val SUPPORT_VIDEO_TYPE = listOf("WMV", "FLV")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_viewer)

        val version = getInstalledViewerVersion()
        val isInstalled = version != null

        findPreference("pref_viewer").let {
            it.setIcon(R.drawable.ic_ondemand_video_36dp)
            it.icon.setTint(AppTheme.getIconColor(it.context))
            it.title = "PacaPlay Viewer"

            if (isInstalled) {
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

        findPreference("pref_viewer_wmv").let {
            it.isEnabled = isInstalled
        }

        findPreference("pref_viewer_flv").let {
            it.isEnabled = isInstalled
        }
    }

}
