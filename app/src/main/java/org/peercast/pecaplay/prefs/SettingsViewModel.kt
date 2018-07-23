package org.peercast.pecaplay.prefs

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.ViewModelProviders
import android.support.v4.app.FragmentActivity
import org.peercast.pecaplay.PecaPlayApplication
import org.peercast.pecaplay.R


class SettingsViewModel (a: Application) : AndroidViewModel(a) {

    private val database = (a as PecaPlayApplication).database
    val yellowPageDao = database.getYellowPageDao()
    val favoriteDao = database.getFavoriteDao()
    val isTablet = a.resources.getBoolean(R.bool.landscape_mode)


//    override fun onCleared() {
//        Log.d(TAG, "onCleared() ")
//    }
//
    companion object {
        private const val TAG = "SettingsViewModel"
        fun get(a: FragmentActivity) = ViewModelProviders.of(a).get(SettingsViewModel::class.java)
    }
}
