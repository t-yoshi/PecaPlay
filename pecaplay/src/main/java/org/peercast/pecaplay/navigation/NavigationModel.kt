package org.peercast.pecaplay.navigation

import android.content.Context
import android.view.Menu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.prefs.PecaPlayPreferences
import org.peercast.pecaplay.yp4g.YpChannel
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

class NavigationModel(private val c: Context) {

    var onChanged = {}

    var items = emptyList<NavigationItem>()
        private set

    private fun parseGenre(channels: List<YpChannel>): List<String> {
        val rS = Regex("[\\s\\-：:]+")
        val tm = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
        channels.flatMap { it.genre.split(rS) }
            .filter { it.isNotBlank() }
            .forEach {
                tm[it] = tm.getOrElse(it) { 0 } + 1
            }
        val g = tm.entries.sortedWith(kotlin.Comparator { a, b ->
            b.value - a.value
        }).filter { it.value > 1 }.map { it.key }
        //Timber.d("--> $g")
        return g
    }

    suspend fun collect(appPrefs: PecaPlayPreferences, database: AppRoomDatabase) {
        combine(
            database.yellowPageDao.query(),
            database.favoriteDao.query(),
            database.ypChannelDao.query(),
        )
        {
                yellowPages: List<YellowPage>,
                favorites: List<Favorite>,
                channels: List<YpChannel>,
            ->
            updateItems(yellowPages, favorites, channels, appPrefs)
        }
            .collect {
                items = it
                onChanged()
            }
    }

    private suspend fun updateItems(
        yellowPages: List<YellowPage>,
        favorites: List<Favorite>,
        channels: List<YpChannel>,
        appPrefs: PecaPlayPreferences,
    ): List<NavigationItem> {
        Timber.d("onUpdate()")
        val items = ArrayList<NavigationItem>(30)

        var topOrder = 0

        items += NavigationHomeItem(c, topOrder++)

        items += NavigationNewItem(c, topOrder++)

        val stars = favorites.filter { it.isStar && !it.flags.isNG }
        items += NavigationStarredItem(c, stars, topOrder++)

        val favoNotify = favorites.filter { it.flags.isNotification && !it.flags.isNG }
        if (appPrefs.isNotificationEnabled) {
            items += NavigationNotifiedItem(c, favoNotify, topOrder++)
        }

        val favoTaggable = favorites.filter { !it.isStar && !it.flags.isNG }
        favoTaggable.forEachIndexed { i, f ->
            items += NavigationFavoriteItem(c, f, i + 10)
        }

        items += NavigationHistoryItem(c, topOrder++)

        yellowPages.forEachIndexed { i, yp ->
            items += NavigationYpItem(c, yp, i + 1)
        }

        parseGenre(channels).take(6).mapIndexed { i, g ->
            items += NavigationGenreItem(c, g, i + 1)
        }

        withContext(Dispatchers.Default) {
            items.filterIsInstance<BadgeableNavigationItem>().forEach { item ->
                val n = channels.count(item.selector)
                val m = channels.filter { !it.isEmptyId }.count(item.selector)
                item.badge = when {
                    n > 99 -> "99+"
                    n > 0 -> "$m"
                    else -> {
                        item.isVisible = false
                        ""
                    }
                }
            }
        }

        return items
    }

}