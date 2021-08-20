package org.peercast.pecaplay.prefs

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.BuildConfig
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import java.util.*

class GeneralPrefsFragment : PreferenceFragmentCompat() {
    private val appPrefs: AppPreferences by inject()
    private val appDatabase: AppRoomDatabase by inject()
    private lateinit var launcher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ res->
            val u: Uri? = res.data?.extras?.getParcelable(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val prefs = checkNotNull(findPreference("pref_notification_sound"))
            prefs.summary = u.toRingtoneTitle()
            appPrefs.notificationSoundUrl = u
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_general)
    }

    override fun onResume() {
        super.onResume()

        checkNotNull(findPreference<EditTextPreference>(DefaultAppPreferences.KEY_PEERCAST_SERVER_URL)).let {
            it.setOnPreferenceChangeListener { _, newValue ->
                val ok = newValue == "" || Uri.parse(newValue as String).run {
                    scheme == "http" &&
                            host?.isNotEmpty() == true &&
                            port > 1024 && path == "/"
                }
                if (ok)
                    it.summary = newValue.toString()
                ok
            }
            it.summary = appPrefs.peerCastUrl.toString()
        }

        checkNotNull(findPreference("pref_header_yellow_page")).let {
            lifecycleScope.launchWhenResumed {
                it.summary = appDatabase.yellowPageDao.query(true)
                    .first().map { it.name }.toString()
            }
        }

        checkNotNull(findPreference("pref_about")).let {
            it.title = "${getString(R.string.app_name)} v${BuildConfig.VERSION_NAME}"
            it.summary = "Build: ${Date(BuildConfig.TIMESTAMP)}"
        }

        checkNotNull(findPreference("pref_notification_sound")).let {
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val i = Intent().apply {
                    action = RingtoneManager.ACTION_RINGTONE_PICKER
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        appPrefs.notificationSoundUrl)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                        getString(R.string.notification_sound))
                }
                launcher.launch(i)
                true
            }
            it.summary = appPrefs.notificationSoundUrl.toRingtoneTitle()
        }

        checkNotNull(findPreference("pref_oss_license")).let {
            it.setOnPreferenceClickListener {
                startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                true
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as AppCompatActivity?)?.supportActionBar?.title =
            getString(R.string.pref_header_general)
    }

    private fun Uri?.toRingtoneTitle(): String {
        val c = requireContext()
        return when {
            this == null || this == Uri.EMPTY -> null
            else -> RingtoneManager.getRingtone(c, this)?.getTitle(c)
        } ?: getString(R.string.none)
    }

}

