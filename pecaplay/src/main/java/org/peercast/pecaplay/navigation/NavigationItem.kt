package org.peercast.pecaplay.navigation

import androidx.annotation.DrawableRes
import org.peercast.pecaplay.yp4g.YpChannelSelector

class NavigationItem(

    val title: CharSequence,

    /**MenuItemのgroupId*/
    val groupId: Int,

    /**グループ内での表示順*/
    val order: Int,

    @DrawableRes val icon: Int,

    val selector: YpChannelSelector,

    tag_: String? = null,
) {
    /**非表示の場合、プリファレンスのキー*/
    val tag = tag_ ?: "$title groupId=$groupId"

    /**バッジ用のテキスト*/
    var badge: String = ""

    var isVisible = true

    val itemId = groupId * 31 + title.hashCode()

    override fun toString(): String = tag
}