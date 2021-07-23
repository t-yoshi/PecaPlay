package org.peercast.pecaplay

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import org.peercast.core.lib.app.BaseClientViewModel
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.chanlist.filter.ChannelFilter
import org.peercast.pecaplay.prefs.PecaPlayPreferences


class PecaPlayViewModel(
    private val a: Application,
    private val pecaPlayPrefs: PecaPlayPreferences,
    private val database: AppRoomDatabase,
) : BaseClientViewModel(a) {
    val presenter = PecaPlayPresenter(this, pecaPlayPrefs, database)

    /**リスト表示用*/
    val channelFilter = ChannelFilter(viewModelScope, database, pecaPlayPrefs)

    /**通知アイコン(ベルのマーク)の有効/無効*/
    val existsNotification = database.favoriteDao.query().map { favorites ->
        favorites.firstOrNull { it.flags.run { !isNG && isNotification } } != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)


    override fun bindService() {
        val u = pecaPlayPrefs.peerCastUrl
        if (u.host in listOf(null, "", "localhost", "127.0.0.1")) {
            super.bindService()
            rpcClient.filterNotNull()
                .onEach { cl ->
                    pecaPlayPrefs.peerCastUrl =
                        Uri.parse("http://localhost:${cl.rpcEndPoint.port}/")
                }
                .launchIn(viewModelScope)
        }
    }
}
