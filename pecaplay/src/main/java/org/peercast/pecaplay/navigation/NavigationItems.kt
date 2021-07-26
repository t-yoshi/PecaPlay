package org.peercast.pecaplay.navigation

import android.content.Context
import android.view.Menu
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.chanlist.filter.YpChannelPredicate
import org.peercast.pecaplay.chanlist.filter.YpChannelPredicates


private const val GID_TOP = Menu.FIRST + 0
private const val GID_FAVORITE = Menu.FIRST + 1
private const val GID_HISTORY = Menu.FIRST + 2
private const val GID_YP = Menu.FIRST + 3
private const val GID_GENRE = Menu.FIRST + 4

class NavigationHomeItem(c: Context, groupOrder: Int) : NavigationItem(
    c.getString(R.string.navigate_all),
    GID_TOP, groupOrder,
    R.drawable.ic_home_36dp) {
    override val selector = YpChannelPredicates.TRUE
    override val key = "home"
}


class NavigationNewItem(c: Context, groupOrder: Int) : BadgeableNavigationItem(
    c.getString(R.string.navigate_newer),
    GID_TOP, groupOrder,
    R.drawable.ic_new_releases_36dp) {
    override val selector: YpChannelPredicate = { ch ->
        !ch.isEmptyId && ch is YpLiveChannel && (ch.numLoaded <= 2)
        //ch.ageAsMinutes < 10 ||
    }
    override val key = "new"
}


class NavigationStarredItem(c: Context, stars: List<Favorite>, groupOrder: Int) : BadgeableNavigationItem(
    c.getString(R.string.navigate_favorite),
    GID_FAVORITE, groupOrder,
    R.drawable.ic_star_36dp) {
    override val selector: YpChannelPredicate = { ch ->
        stars.any { it.matches(ch) }
    }
    override val key = "starred"
}


class NavigationFavoriteItem(c: Context, favo: Favorite, groupOrder: Int) : BadgeableNavigationItem(
    favo.name,
    GID_FAVORITE, groupOrder,
    R.drawable.ic_bookmark_36dp) {
    override val selector: YpChannelPredicate = { ch ->
        favo.matches(ch)
    }
    override val key = "favorite (${favo.name})"
}

class NavigationNotifiedItem(c: Context, favoNotify: List<Favorite>, groupOrder: Int) : BadgeableNavigationItem(
    c.getString(R.string.notified),
    GID_FAVORITE, groupOrder,
    R.drawable.ic_notifications_36dp){
    override val selector: YpChannelPredicate = { ch->
        favoNotify.any {
            //ch is YpIndex && ch.numLoaded < 3 &&
            it.matches(ch)
        }
    }
    override val key = "notified"
}


class NavigationHistoryItem(c: Context, groupOrder: Int) : NavigationItem(
    c.getString(R.string.navigate_history),
    GID_HISTORY, groupOrder,
    R.drawable.ic_history_36dp) {
    override val selector: YpChannelPredicate = YpChannelPredicates.TRUE
    override val key: String = "history"
}

class NavigationYpItem(c: Context, yp: YellowPage, groupOrder: Int) : BadgeableNavigationItem(
    yp.name,
    GID_YP, groupOrder,
    R.drawable.ic_peercast) {
    override val selector: YpChannelPredicate = {
        it.ypName == yp.name
    }
    override val key = "yellowpage (${yp.name})"
}

class NavigationGenreItem(c: Context, genre: String, groupOrder: Int) : BadgeableNavigationItem(
    genre, GID_GENRE, groupOrder,
    R.drawable.ic_bookmark_border_36dp) {
    override val selector: YpChannelPredicate = { ch ->
        ch.genre.contains(genre, true)
    }
    override val key = "genre ($genre)"
}

