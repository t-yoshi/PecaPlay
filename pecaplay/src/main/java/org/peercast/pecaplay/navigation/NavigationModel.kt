package org.peercast.pecaplay.navigation

import android.content.Context
import android.view.Menu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.app.YpLiveChannel
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

        items += NavigationItem(
            c.getString(R.string.navigate_all),
            GID_TOP, topOrder++,
            R.drawable.ic_home_36dp,
            { true }, TAG_HOME
        )

        items += NavigationItem(
            c.getString(R.string.navigate_newer),
            GID_TOP, topOrder++,
            R.drawable.ic_new_releases_36dp,
            { ch ->
                !ch.isEmptyId && ch is YpLiveChannel && (ch.numLoaded <= 2)
            }//ch.ageAsMinutes < 15 ||
        )

        val stars = favorites.filter { it.isStar && !it.flags.isNG }
        items += NavigationItem(
            c.getString(R.string.navigate_favorite),
            GID_FAVORITE, topOrder++,
            R.drawable.ic_star_36dp,
            { ch ->
                stars.any { it.matches(ch) }
            })

        val favoNotify = favorites.filter { it.flags.isNotification && !it.flags.isNG }
        if (appPrefs.isNotificationEnabled) {
            items += NavigationItem(
                c.getString(R.string.notificated),
                GID_FAVORITE, topOrder++,
                R.drawable.ic_notifications_36dp,
                { ch ->
                    favoNotify.any {
                        //ch is YpIndex && ch.numLoaded < 3 &&
                        it.matches(ch)
                    }
                },
                TAG_NOTIFICATED
            )
        }

        val favoTaggable = favorites.filter { !it.isStar && !it.flags.isNG }
        favoTaggable.forEachIndexed { i, favo ->
            items += NavigationItem(
                favo.name,
                GID_FAVORITE, i + 10,
                R.drawable.ic_bookmark_36dp,
                { ch ->
                    favo.matches(ch)
                })
        }


        items += NavigationItem(
            c.getString(R.string.navigate_history),
            GID_HISTORY, topOrder++,
            R.drawable.ic_history_36dp,
            { true },
            TAG_HISTORY
        )

        yellowPages.forEachIndexed { i, yp ->
            items += NavigationItem(
                yp.name,
                GID_YP, i + 1,
                R.drawable.ic_peercast,
                {
                    it.ypName == yp.name
                })
        }

        parseGenre(channels).take(6).mapIndexed { i, t ->
            items += NavigationItem(
                t, GID_GENRE, i + 1,
                R.drawable.ic_bookmark_border_36dp,
                {
                    it.genre.contains(t, true)
                })
        }

        val badgeInvisibleItems = listOf(TAG_HOME, TAG_HISTORY)

        withContext(Dispatchers.Default) {
            items.filter { it.tag !in badgeInvisibleItems }.forEach {
                val n = channels.count(it.selector)
                val m = channels.filter { !it.isEmptyId }.count(it.selector)
                it.badge = when {
                    n > 99 -> "99+"
                    n > 0 -> "$m"
                    else -> {
                        it.isVisible = false
                        ""
                    }
                }
            }
        }

        return items
    }

    companion object {
        //グループ
        const val GID_TOP = Menu.FIRST + 0
        const val GID_FAVORITE = Menu.FIRST + 1
        const val GID_HISTORY = Menu.FIRST + 2
        const val GID_YP = Menu.FIRST + 3
        const val GID_GENRE = Menu.FIRST + 4

        const val TAG_HOME = "home"
        const val TAG_NEWLY = "newly"
        const val TAG_NOTIFICATED = "notificated"
        const val TAG_HISTORY = "history"
    }
}