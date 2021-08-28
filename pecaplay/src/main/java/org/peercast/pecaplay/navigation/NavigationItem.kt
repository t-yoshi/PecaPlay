package org.peercast.pecaplay.navigation

import androidx.annotation.DrawableRes
import org.peercast.pecaplay.chanlist.filter.YpChannelPredicate
import java.util.*


abstract class NavigationItem(

    val title: CharSequence,

    /**MenuItemのgroupId*/
    val groupId: Int,

    /**グループ内での表示順*/
    val order: Int,

    @DrawableRes val icon: Int,
) {

    /**非表示設定のキー*/
    abstract val key: String

    abstract val selector: YpChannelPredicate


    var isVisible = true

    val itemId get() = hashCode()

    override fun hashCode() = Objects.hash(groupId, title)

    override fun equals(other: Any?): Boolean {
        return other is NavigationItem &&
                other.javaClass == javaClass &&
                other.key == key
    }

    override fun toString() = key
}

abstract class BadgeableNavigationItem(title: CharSequence, groupId: Int, order: Int, icon: Int) :
    NavigationItem(
        title,
        groupId,
        order,
        icon
    ) {

    /**バッジ用のテキスト*/
    var badge: String = ""

    override fun hashCode() = super.hashCode() * 31 + badge.hashCode()

    override fun equals(other: Any?): Boolean {
        return other is BadgeableNavigationItem &&
                other.javaClass == javaClass &&
                other.badge == badge
    }

}
