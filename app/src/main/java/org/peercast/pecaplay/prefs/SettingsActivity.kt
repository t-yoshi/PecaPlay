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

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
            delegate.setLocalNightMode(AppCompatDelegate.getDefaultNightMode())
        }
        super.onCreate(savedInstanceState)

        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
        }

        when(intent.action){
            ACTION_FAVORITE_PREFS -> replaceFragment(FavoritePrefsFragment())
            else -> replaceFragment(GeneralPrefsFragment())
        }
    }

    private fun replaceFragment(frag: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, frag)
            .commit()
    }



    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val f = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment, null)
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
        private const val ACTION_FAVORITE_PREFS = "org.peercast.pecaplay.prefs.ACTION_FAVORITE_PREFS"

        fun startFavoritePrefs(a: AppCompatActivity) {
            a.startActivity(Intent(ACTION_FAVORITE_PREFS, null, a, SettingsActivity::class.java))
        }

        fun startGeneralPrefs(a: AppCompatActivity) {
            a.startActivity(Intent(a, SettingsActivity::class.java))
        }
    }

}

/*
*  fun startViewerPendingIntent(c: Context, ch: YpChannel): PendingIntent {
        //通知バーから視聴を再開した場合に戻るボタンでPecaPlayに帰って来れるようにする
        val taskStack = TaskStackBuilder.create(c)
            .addNextIntent(createPecaPlayIntent(c))
            .addNextIntent(createViewerIntent(ch))
        return taskStack.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
    }
* */
