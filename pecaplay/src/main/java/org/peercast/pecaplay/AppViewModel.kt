package org.peercast.pecaplay

import android.app.Application
import android.net.Uri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import org.peercast.core.lib.app.BaseClientViewModel
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.chanlist.filter.ChannelFilter
import org.peercast.pecaplay.core.io.localizedSystemMessage
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.worker.LoadingEvent
import org.peercast.pecaplay.worker.LoadingEventFlow
import retrofit2.HttpException


class AppViewModel(
    private val a: Application,
    private val appPrefs: AppPreferences,
    private val database: AppRoomDatabase,
    private val loadingEvent: LoadingEventFlow,
) : BaseClientViewModel(a) {
    val presenter = AppViewModelPresenter(this, appPrefs, database)

    /**リスト表示用*/
    val channelFilter = ChannelFilter(viewModelScope, database, appPrefs)

    /**通知アイコン(ベルのマーク)の有効/無効*/
    val existsNotification = MutableStateFlow(false)

    /**Snackbarで表示するメッセージ*/
    val message = MutableStateFlow<CharSequence>("")

    init {
        database.favoriteDao.query().map { favorites ->
            favorites.firstOrNull { it.flags.run { !isNG && isNotification } } != null
        }.onEach { existsNotification.value = it }
            .launchIn(viewModelScope)

        rpcClient.filterNotNull().onEach { client ->
            message.value = a.getString(R.string.peercast_has_started, client.rpcEndPoint.port)
        }.launchIn(viewModelScope)

        loadingEvent.filterIsInstance<LoadingEvent.OnException>().onEach { ev ->
            val s = when (ev.e) {
                is HttpException -> ev.e.response()?.message()
                    ?: ev.e.localizedSystemMessage()
                else -> ev.e.localizedSystemMessage()
            }
            message.value = HtmlCompat.fromHtml("<font color=red>${ev.yp.name}: $s", 0)
        }.launchIn(viewModelScope)

    }

    override fun bindService() {
        val u = appPrefs.peerCastUrl
        if (u.host in listOf(null, "", "localhost", "127.0.0.1")) {
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
