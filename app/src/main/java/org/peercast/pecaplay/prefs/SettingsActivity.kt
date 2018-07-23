package org.peercast.pecaplay.prefs

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.peercast.pecaplay.R

class SettingsActivity : AppCompatPreferenceActivity () {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onBuildHeaders(target: MutableList<Header>?) {
        loadHeadersFromResource(R.xml.prefs_headers, target);
    }

    override fun isValidFragment(fragmentName: String?): Boolean {
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (fragmentManager.backStackEntryCount > 0){
                    fragmentManager.popBackStack()
                }else{
                    finish()
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }

    }


}


