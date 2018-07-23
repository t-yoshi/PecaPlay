package org.peercast.pecaplay.prefs

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.support.v7.app.AlertDialog
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import org.peercast.pecaplay.BuildConfig
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppNightMode
import org.peercast.pecaplay.app.AppTheme
import java.util.*

class GeneralPrefsFragment : PreferenceFragment(),
        SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var appPrefs: AppPreferences
    private lateinit var pref_notification_sound: Preference

    private fun preferences() = preferenceScreen.run {
        (0 until preferenceCount).map {
            preferenceScreen.getPreference(it)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.pref_general)
        pref_notification_sound = findPreference("pref_notification_sound")
        appPrefs = AppPreferences(context)
        appPrefs.registerOnSharedPreferenceChangeListener(this)

        findPreference("pref_peercast_server_url").setOnPreferenceChangeListener { _, newValue ->
            newValue == "" || Uri.parse(newValue as String).run {
                scheme == "http" && host.isNotEmpty() && port > 80 && path == "/"
            }
        }

        findPreference("pref_about").apply {
            title = "$title v${BuildConfig.VERSION_NAME}"
            summary = "Build: ${Date(BuildConfig.TIMESTAMP)}"
        }

        pref_notification_sound.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivityForResult(Intent().apply {
                    action = RingtoneManager.ACTION_RINGTONE_PICKER
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, appPrefs.notificationSoundUrl)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.notification_sound))
                }, REQ_SOUND_PICKER)
                true
            }
        }

        with(findPreference("pref_oss_license")) {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val text = resources.assets.open("oss_credit.txt").reader().readText()
                AlertDialog.Builder(activity)
                        .setTitle(R.string.oss_license)
                        .setView(R.layout.oss_license_dialog)
                        //.setView(view)
                        .setPositiveButton(android.R.string.ok, null)
                        .create().apply {
                            setOnShowListener {
                                findViewById<TextView>(android.R.id.text1)!!.also {
                                    it.text = text
                                    it.movementMethod = ScrollingMovementMethod.getInstance()
                                }
                            }
                            show()
                        }
                true
            }
        }


        val tint = AppTheme(context!!).iconTint
        preferences().forEach {
            updateSummary(it)
            it.icon?.setTintList(tint)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null)
            return

        when (requestCode) {
            REQ_SOUND_PICKER -> {
                //Timber.d("${data.extras.keySet().toList()}")
                appPrefs.notificationSoundUrl =
                        data.extras.getParcelable(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                updateSummary(pref_notification_sound)
            }
        }
    }

    private fun updateSummary(pref: Preference) {
        when {
            pref is ListPreference -> {
                pref.value
            }
            pref is EditTextPreference -> {
                pref.text
            }
            pref === pref_notification_sound -> {
                val u = appPrefs.notificationSoundUrl
                u?.let {
                    RingtoneManager.getRingtone(context, it)?.getTitle(context) ?: u.toString()
                } ?: getString(R.string.none)
            }
            else -> null
        }.let {
            pref.summary = it
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        findPreference(key)?.let {
            updateSummary(it)
        }

        when {
            key == AppPreferences.KEY_NIGHT_MODE -> {
                AppNightMode.setMode(activity, prefs.getString(key, "?"))
            }
        }
    }


/*
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setupGeneralScreen()
        setupNotification()
        setupAbout()
    }

    private fun setupGeneralScreen() {
        addPreferencesFromResource(R.xml.pref_general)

        with(findPreferenceAs<EditTextPreference>(AppPreferences.KEY_PEERCAST_URL)) {
            val isInstalled = PeerCastServiceController(activity).isInstalled
            isSelectable = !isInstalled
            if (isInstalled)
                title = title.toString() + " (Auto)"

            onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
                val u = newValue as String
                if (!u.startsWith("http://"))
                    return@OnPreferenceChangeListener false
                preference.summary = u
                true
            }
            initSummary()
        }

        with(findPreferenceAs<SwitchPreference>(AppPreferences.KEY_SAVE_SEARCH_HISTORY)) {
            onPreferenceChangeListener = OnPreferenceChangeListener { pref, newValue ->
                val b = newValue as Boolean
                if (!b) {
                    val suggestions = SearchRecentSuggestions(
                            activity,
                            SuggestionProvider.AUTHORITY,
                            SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES)
                    suggestions.clearHistory()
                    Toast.makeText(activity, "Clear search history.", Toast.LENGTH_LONG).show()
                }
                true
            }

        }

        with(findPreferenceAs<ListPreference>(AppPreferences.KEY_THEME)) {
            entries = AppTheme.labels()
            entryValues = AppTheme.names()
            setDefaultValue(AppTheme.names()[0])

            onPreferenceChangeListener = OnPreferenceChangeListener { pref, newValue ->
                pref.updateSummary(newValue)
                setUiMode(activity, newValue as String)
                true
            }
            initSummary()
        }
    }

    private fun setupNotification() {
        val cat = findPreferenceAs<PreferenceCategory>("pref_category_notification")
        val flagNotification =
                cat.findPreferenceAs<MultiSelectListPreference>(AppPreferences.KEY_NOTIFICATION_TYPES)
        val notificationSound =
                cat.findPreferenceAs<RingtonePreference>(AppPreferences.KEY_NOTIFICATION_SOUND_URL)

        with(flagNotification) {
            entries = NotificationType.labels(activity)
            entryValues = NotificationType.names()
            setDefaultValue(NotificationType.defaultValues())

            onPreferenceChangeListener = OnPreferenceChangeListener { _, v ->
                notificationSound.isSchedulable = (v as Set<String>).contains(NotificationType.Sound.name)
                updateSummary(v)
                true
            }
            initSummary()
        }

        with(notificationSound) {
            isSchedulable = flagNotification.values.contains(NotificationType.Sound.name)
            onPreferenceChangeListener = UPDATE_SUMMARY_LISTENER
            initSummary()
        }
    }

    private fun setupAbout() {
        val cat = findPreferenceAs<PreferenceCategory>("pref_category_about")

        with(cat.findPreference("pref_about")) {
            //Intent i = new Intent(Intent.ACTION_VIEW);
            //i.setData(Uri.parse("market://details?id=org.peercast.pecaplay"));
            //prefAbout.setIntent(i);
            title = getString(R.string.app_version_full, BuildConfig.VERSION_NAME)
            val buildDate = DateFormat.format("yyyy/MM/dd kk:mm", BuildConfig.TIMESTAMP)
            summary = " build ($buildDate) "

            onPreferenceClickListener = object : Preference.OnPreferenceClickListener {
                internal var clicked: Int = 0

                override fun onPreferenceClick(preference: Preference): Boolean {
                    if (++clicked == 5) {
                        val prefDebug = SwitchPreference(activity)
                        prefDebug.key = AppPreferences.KEY_APP_DEBUG_MODE
                        prefDebug.setTitle(R.string.t_debug_mode)
                        prefDebug.setDefaultValue(false)
                        cat.addPreference(prefDebug)

                        onPreferenceClickListener = null
                    } else {
                        summary = preference.summary.toString() + "."
                    }
                    return true
                }
            }
        }

        with(findPreference("pref_opensource_license")) {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                try {
                    val text = resources.assets.open("oss_credit.txt").reader().readText()
                    AlertDialog.Builder(activity)
                            .setTitle(R.string.t_oss_license)
                            .setMessage(text)
                            .show()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                false
            }
        }
    }
*/

    companion object {
        private const val REQ_SOUND_PICKER = 1
    }
}

