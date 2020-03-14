package org.peercast.pecaplay.prefs

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            delegate.localNightMode = AppCompatDelegate.getDefaultNightMode()
        }
        super.onCreate(savedInstanceState)

        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
        }

        when (intent.action) {
            ACTION_FAVORITE_PREFS -> replaceFragment(FavoritePrefsFragment())
            else -> replaceFragment(GeneralPrefsFragment())
        }
    }

    private fun replaceFragment(frag: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, frag)
            .commit()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val f = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)
        supportFragmentManager.beginTransaction()
            .addToBackStack(null)
            //.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(android.R.id.content, f)
            .commit()
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

        /**夜間モードが変更された(lollipop only)*/
        const val RESULT_NIGHT_MODE_CHANGED = RESULT_FIRST_USER
    }
}

