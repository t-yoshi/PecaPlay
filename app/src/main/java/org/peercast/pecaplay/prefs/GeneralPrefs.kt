package org.peercast.pecaplay.prefs

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.BuildConfig
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.AppTheme
import java.util.*

class GeneralPrefsFragment : PreferenceFragmentCompat() {
    private val appPrefs: AppPreferences by inject()
    private val appDatabase: AppRoomDatabase by inject()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_general)
    }

    override fun onResume() {
        super.onResume()

        with(findPreference<EditTextPreference>(AppPreferences.KEY_PEERCAST_SERVER_URL) as EditTextPreference) {
            setOnPreferenceChangeListener { _, newValue ->
                val ok = newValue == "" || Uri.parse(newValue as String).run {
                    scheme == "http" &&
                            host?.isNotEmpty() == true &&
                            port > 1024 && path == "/"
                }
                if (ok)
                    summary = newValue.toString()
                ok
            }
            summary = appPrefs.peerCastUrl.toString()
        }

        with(findPreference<Preference>("pref_header_yellow_page")!!) {
            lifecycleScope.launchWhenResumed {
                summary = appDatabase.yellowPageDao.queryAwait(true).map { it.name }.toString()
            }
        }

        with(findPreference<Preference>("pref_header_viewer")!!) {
            summary = PecaPlayViewerSetting.enabledTypes.toString()
        }

        with(findPreference<SwitchPreference>(AppPreferences.KEY_IS_NIGHT_MODE) as SwitchPreference) {
            setOnPreferenceChangeListener { _, newValue ->
                lifecycleScope.launchWhenResumed {
                    delay(250)
                    AppTheme.initNightMode(context, newValue as Boolean)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        activity?.let { a ->
                            //a.recreate()
                            a.setResult(SettingsActivity.RESULT_NIGHT_MODE_CHANGED)
                        }
                    }
                }
                true
            }
        }

        findPreference<Preference>("pref_about")!!.let {
            it.title = "${getString(R.string.app_name)} v${BuildConfig.VERSION_NAME}"
            it.summary = "Build: ${Date(BuildConfig.TIMESTAMP)}"
        }

        findPreference<Preference>("pref_notification_sound")!!.let {
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivityForResult(Intent().apply {
                    action = RingtoneManager.ACTION_RINGTONE_PICKER
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        appPrefs.notificationSoundUrl)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                        getString(R.string.notification_sound))
                }, REQ_SOUND_PICKER)
                true
            }
            it.summary = getNotificationSoundTitle(appPrefs.notificationSoundUrl)
        }

        with(findPreference<Preference>("pref_oss_license")!!) {
            setOnPreferenceClickListener {
                startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                true
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as AppCompatActivity?)?.supportActionBar?.title =
            getString(R.string.pref_header_general)
    }

    private fun getNotificationSoundTitle(u: Uri?): String {
        val c = context
        return when {
            u != null && u != Uri.EMPTY ->
                RingtoneManager.getRingtone(c, u)?.getTitle(c)
            else -> null
        } ?: getString(R.string.none)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null)
            return

        when (requestCode) {
            REQ_SOUND_PICKER -> {
                val u: Uri? = data.extras?.getParcelable(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                //Timber.d("${data.extras.keySet().toList()}")
                findPreference<Preference>("pref_notification_sound")!!.summary =
                    getNotificationSoundTitle(u)
                appPrefs.notificationSoundUrl = u
            }
        }
    }

    companion object {
        private const val REQ_SOUND_PICKER = 1
    }
}

