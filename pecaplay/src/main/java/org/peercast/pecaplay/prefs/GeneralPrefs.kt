package org.peercast.pecaplay.prefs

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.BuildConfig
import org.peercast.pecaplay.PecaPlayNotification
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import java.util.*

class GeneralPrefsFragment : PreferenceFragmentCompat() {
    private val appPrefs: AppPreferences by inject()
    private val appDatabase: AppRoomDatabase by inject()

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
            lifecycleScope.launch {
                it.summary = appDatabase.yellowPageDao.query(true)
                    .first().map { it.name }.toString()
            }
        }

        checkNotNull(findPreference("pref_about")).let {
            it.title = "${getString(R.string.app_name)} v${BuildConfig.VERSION_NAME}"
            it.summary = "Build: ${Date(BuildConfig.TIMESTAMP)}"
        }

        checkNotNull(findPreference("pref_notification_sound")).let {
            it.isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

            @TargetApi(Build.VERSION_CODES.O)
            it.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
                PecaPlayNotification(it.context).launchSystemSettings(this)
                true
            }
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

}

