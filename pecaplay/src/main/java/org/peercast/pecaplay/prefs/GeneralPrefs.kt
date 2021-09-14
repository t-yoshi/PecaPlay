package org.peercast.pecaplay.prefs

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import kotlinx.coroutines.Dispatchers
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

    private fun initPreferences() {
        checkNotNull(findPreference<EditTextPreference>(DefaultAppPreferences.KEY_PEERCAST_SERVER_URL)).let {
            it.setOnBindEditTextListener { et ->
                et.inputType = EditorInfo.TYPE_TEXT_VARIATION_URI
            }
            it.setOnPreferenceChangeListener { _, newValue ->
                val valid = Uri.parse(newValue as String).let { u ->
                    u.scheme == "http" &&
                            u.host.let { h -> h != null && isPrivateAddress(h) } &&
                            u.port in 1025..65535 &&
                            u.path == "/"
                } || newValue.isEmpty()
                if (valid) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        initPreferences()
                    }
                }
                valid
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

    override fun onResume() {
        super.onResume()
        initPreferences()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as AppCompatActivity?)?.supportActionBar?.title =
            getString(R.string.pref_header_general)
    }

    companion object {
        private fun isPrivateAddress(ip: String): Boolean {
            if (ip in listOf("localhost", "127.0.0.1"))
                return true
            val n = ip.split(".")
                .mapNotNull { it.toUByteOrNull()?.toInt() }
            return when {
                n.size != 4 -> false

                // a)
                n[0] == 192 && n[1] == 168 -> true

                // b)
                n[0] == 172 && n[1] in 16..31 -> true

                // c)
                n[0] == 10 -> true

                else -> false
            }
        }
    }

}

