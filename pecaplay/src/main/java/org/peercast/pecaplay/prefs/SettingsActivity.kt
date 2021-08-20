package org.peercast.pecaplay.prefs

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            val f = when (intent.action) {
                ACTION_FAVORITE_PREFS -> FavoritePrefsFragment()
                else -> GeneralPrefsFragment()
            }
            supportFragmentManager.commit {
                replace(android.R.id.content, f)
            }
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val f = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)
        supportFragmentManager.commit {
            addToBackStack(null)
            //setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            replace(android.R.id.content, f)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val ACTION_FAVORITE_PREFS =
            "org.peercast.pecaplay.prefs.ACTION_FAVORITE_PREFS"
    }
}

