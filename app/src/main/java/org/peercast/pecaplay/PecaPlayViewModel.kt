package org.peercast.pecaplay

import android.app.Application
import android.net.Uri
import androidx.core.net.UriCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.peercast.core.lib.app.BasePeerCastViewModel
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.util.TextUtils.normalize
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.YpDisplayOrder
import org.peercast.pecaplay.yp4g.descriptionOrGenre
import kotlin.properties.Delegates


class PecaPlayViewModel(
    a: Application,
    private val appPrefs: AppPreferences,
    private val database: AppRoomDatabase,
) : BasePeerCastViewModel(a) {
    val presenter = PecaPlayPresenter(this, appPrefs, database)

    private val liveChannelFlow = database.ypChannelDao.query2()

    private val historyChannelFlow = combine(
        database.ypChannelDao.query2(),
        database.ypHistoryDao.query2()
    ) { channels, histories ->
        withContext(Dispatchers.Default) {
            histories.forEach { his ->
                //現在存在して再生可能か
                his.isEnabled = channels.any(his::equalsIdName)
            }
            histories
        }
    }

    /**リスト表示用*/
    val channelsFlow: Flow<List<YpChannel>> = MutableStateFlow(emptyList())

    private var j: Job? = null

    private fun changeSource(src: YpChannelSource) {
        j?.cancel()
        val f = when (src) {
            YpChannelSource.NONE -> MutableStateFlow(emptyList())
            YpChannelSource.LIVE -> liveChannelFlow
            YpChannelSource.HISTORY -> historyChannelFlow
        }
        j = viewModelScope.launch {
            f.onEach { channels ->
                var l = channels.filter(selector)

                if (searchQuery.isNotBlank()) {
                    val constraints = searchQuery.normalize().split(RE_SPACE)
                    l = l.filter { ch ->
                        constraints.all { ch.searchText.contains(it) }
                    }
                }

                (channelsFlow as MutableStateFlow).value = when (displayOrder) {
                    YpDisplayOrder.NONE -> l
                    else -> l.sortedWith(displayOrder.comparator)
                }
            }
                .flowOn(Dispatchers.Default)
                .collect()
        }
    }


    /**配信中or履歴**/
    var source by Delegates.observable(YpChannelSource.NONE) { _, old, new ->
        if (old != new)
            changeSource(new)
    }

    /**お気に入り/ジャンル等で選別するセレクタ*/
    var selector by Delegates.observable<(YpChannel) -> Boolean>({ true }) { _, old, new ->
        if (old != new)
            changeSource(source)
    }

    /**表示する順序*/
    var displayOrder by Delegates.observable(appPrefs.displayOrder) { _, old, new ->
        if (old != new)
            changeSource(source)
    }

    /**検索窓から*/
    var searchQuery by Delegates.observable("") { _, old, new ->
        if (old != new)
            changeSource(source)
    }


    /**通知アイコン(ベルのマーク)の有効/無効*/
    @Deprecated("")
    val isNotificationIconEnabled: LiveData<Boolean> = Transformations.map(
        database.favoriteDao.query()
    ) { favorites ->
        favorites.firstOrNull { it.flags.run { !isNG && isNotification } } != null
    }

    /**通知アイコン(ベルのマーク)の有効/無効*/
    val existsNotification =
        database.favoriteDao.query2().map { favorites ->
            favorites.firstOrNull { it.flags.run { !isNG && isNotification } } != null
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun bindService() {
        val u = appPrefs.peerCastUrl
        if (u.host in listOf(null, "", "localhost", "127.0.0.1")) {
            super.bindService(null)
            rpcClient.filterNotNull()
                .onEach { cl->
                    appPrefs.peerCastUrl = Uri.parse("http://${u.host}:${cl.rpcEndPoint.port}/")
                }
                .launchIn(viewModelScope)
        }
    }

    companion object {
        private val RE_SPACE = """[\s　]+""".toRegex()

        private val YpChannel.searchText: String
            get() = yp4g.run { "$name $comment $descriptionOrGenre" }.normalize()

    }
}

