package org.peercast.pecaplay

import android.app.Application
import android.net.Uri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.peercast.core.lib.app.BaseClientViewModel
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.chanlist.filter.ChannelFilter
import org.peercast.pecaplay.core.io.isLoopbackAddress
import org.peercast.pecaplay.core.io.localizedSystemMessage
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.worker.LoadingEvent
import org.peercast.pecaplay.worker.LoadingEventFlow
import retrofit2.HttpException


class AppViewModel(
    a: Application,
    private val appPrefs: AppPreferences,
    database: AppRoomDatabase,
    loadingEvent: LoadingEventFlow,
) : BaseClientViewModel(a) {
    val presenter = AppViewModelPresenter(this, appPrefs, database)

    /**通知アイコン(ベルのマーク)ボタンの有効/無効*/
    val notificationIconEnabled = database.favoriteDao.query().map { favorites ->
        favorites.firstOrNull { it.flags.run { !isNG && isNotification } } != null
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)


    /**Snackbarで表示するメッセージ*/
    val message = Channel<CharSequence>()

    val channelFilter = ChannelFilter(database, appPrefs)

    init {
        loadingEvent.filterIsInstance<LoadingEvent.OnException>().onEach { ev ->
            val s = when (ev.e) {
                is HttpException -> ev.e.response()?.message()
                    ?: ev.e.localizedSystemMessage()
                else -> ev.e.localizedSystemMessage()
            }
            val h = HtmlCompat.fromHtml("<font color=red>${ev.yp.name}: $s", 0)
            message.send(h)
        }.launchIn(viewModelScope)
    }

    override fun bindService() {
        val u = appPrefs.peerCastUrl
        if (u.isLoopbackAddress()) {
            super.bindService()
            rpcClient.filterNotNull()
                .onEach { cl ->
                    appPrefs.peerCastUrl =
                        Uri.parse("http://localhost:${cl.rpcEndPoint.port}/")
                }
                .launchIn(viewModelScope)
        }
    }
}
