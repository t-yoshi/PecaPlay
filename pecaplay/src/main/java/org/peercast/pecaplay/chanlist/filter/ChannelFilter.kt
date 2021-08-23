package org.peercast.pecaplay.chanlist.filter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.chanlist.ListItemViewModel
import org.peercast.pecaplay.navigation.NavigationHistoryItem
import org.peercast.pecaplay.navigation.NavigationItem
import org.peercast.pecaplay.navigation.NavigationNewItem
import org.peercast.pecaplay.navigation.NavigationNotifiedItem
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.util.TextUtils.normalize
import org.peercast.pecaplay.yp4g.YpChannel
import org.peercast.pecaplay.yp4g.YpDisplayOrder

class ChannelFilter(
    private val db: AppRoomDatabase,
    appPrefs: AppPreferences,
) {

    /**検索文字列*/
    val searchQuery = MutableStateFlow("")

    /**表示順*/
    val displayOrder = MutableStateFlow(appPrefs.displayOrder)

    /***/
    val navigationItem = MutableStateFlow<NavigationItem?>(null)

    private val selectedChannels = combine(
        navigationItem,
        db.ypChannelDao.query(),
        db.ypHistoryDao.query(),
        searchQuery,
        displayOrder,
    ) { item, lives, histories, query, _order ->
        //Timber.d("--> $item, ${lives.size}, ${histories.size}, $query, $_order")
        if (item == null)
            return@combine TaggedList("", emptyList())

        //メニューで選択した表示順よりも優先
        val order = when (item) {
            is NavigationHistoryItem -> YpDisplayOrder.NONE
            is NavigationNotifiedItem,
            is NavigationNewItem,
            -> {
                YpDisplayOrder.AGE_ASC
            }
            else -> _order
        }

        //Timber.d("--> $item ${Thread.currentThread()}")
        when (item) {
            is NavigationHistoryItem -> {
                histories.map {
                    //現在配信中ならそれを表示する
                    lives.firstOrNull(it::equalsIdName) ?: it
                }
            }
            else -> lives
        }
            .filter(item.selector)
            .filterQuery(query)
            .sortedWithDisplayOrder(order)
            .let { TaggedList("${item.key}#$order", it) }
    }


    private fun List<YpChannel>.filterQuery(query: String): List<YpChannel> {
        if (query.isBlank())
            return this
        val constraints = query.normalize().split(RE_SPACE)
        return filter { ch ->
            constraints.all { ch.searchText.contains(it) }
        }
    }

    private fun List<YpChannel>.sortedWithDisplayOrder(order: YpDisplayOrder): List<YpChannel> {
        return when (order) {
            YpDisplayOrder.NONE -> this
            else -> sortedWith(order.comparator)
        }
    }


    fun toListItemViewModels(c: Context): Flow<TaggedList<ListItemViewModel>> {
        return combine(
            selectedChannels, db.favoriteDao.query()
        ) { channels, favorites ->
            val (favNg, favo) = favorites.partition { it.flags.isNG }
            channels.map { ch ->
                val star = favo.firstOrNull { it.isStar && it.matches(ch) }
                val isNg = star == null && favNg.any { it.matches(ch) }
                val isNotification =
                    favo.filter { it.flags.isNotification }.any { it.matches(ch) }
                ListItemViewModel(c, ch, star, isNg, isNotification)
            }.let {
                TaggedList(channels.tag, it)
            }
        }.flowOn(Dispatchers.Default)
    }


    companion object {
        private val RE_SPACE = """[\s　]+""".toRegex()

        private val YpChannel.searchText: String
            get() = run { "$name $genre $description $comment" }.normalize()

    }
}