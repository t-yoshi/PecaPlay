package org.peercast.pecaplay.chanlist.filter

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.util.TextUtils.normalize
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.YpDisplayOrder

class ChannelFilter(
    private val scope: CoroutineScope,
    private val db: AppRoomDatabase,
    private val prefs: AppPreferences,
) {
    var params: FilterParams = MutableFilterParams(
        YpChannelSource.LIVE, YpChannelPredicates.TRUE, prefs.displayOrder, ""
    )
        private set

    val filteredChannel: StateFlow<FilteredChannelList> = MutableStateFlow(
        FilteredChannelList(emptyList(), params)
    )

    private val liveChannel = db.ypChannelDao.query()
    private val historyChannel = combine(
        db.ypChannelDao.query(),
        db.ypHistoryDao.query()
    ) { channels, histories ->
        withContext(Dispatchers.Default) {
            histories.map { his ->
                //現在存在して再生可能か
                async {
                    his.isPlayable = channels.any(his::equalsIdName)
                }
            }.awaitAll()
            histories
        }
    }

    private var j: Job? = null

    private fun onFilterParamsChanged(params: FilterParams) {
        j?.cancel()
        j = scope.launch(Dispatchers.Default) {
            //Timber.d("onFilterParamsChanged: $params")
            when (params.source) {
                YpChannelSource.LIVE -> liveChannel
                YpChannelSource.HISTORY -> historyChannel
            }.onEach { channels ->
                //Timber.d("-> channels $channels")
                var l = channels.filter(params.selector)

                if (params.searchQuery.isNotBlank()) {
                    val constraints = params.searchQuery.normalize().split(RE_SPACE)
                    l = l.filter { ch ->
                        constraints.all { ch.searchText.contains(it) }
                    }
                }

                l = when (params.displayOrder) {
                    YpDisplayOrder.NONE -> l
                    else -> l.sortedWith(params.displayOrder.comparator)
                }

                (filteredChannel as MutableStateFlow).value = FilteredChannelList(l, params)
            }.collect()
        }
    }

    init {
        onFilterParamsChanged(params)
    }

    /**変更後に適用する*/
    fun apply(action: MutableFilterParams.() -> Unit) {
        val copy = (params as MutableFilterParams).copy()
        copy.action()
        //Timber.d(" -> $params")
        if (copy != params) {
            params = copy
            onFilterParamsChanged(copy)
        }
    }

    data class MutableFilterParams(
        override var source: YpChannelSource,
        override var selector: (YpChannel) -> Boolean,
        override var displayOrder: YpDisplayOrder,
        override var searchQuery: String,
    ) : FilterParams()


    companion object {
        private val RE_SPACE = """[\s　]+""".toRegex()

        private val YpChannel.searchText: String
            get() = run { "$name $genre $description $comment" }.normalize()

    }
}