package org.peercast.pecaplay.prefs


import android.net.Uri
import org.peercast.pecaplay.yp4g.YpDisplayOrder

abstract class PecaPlayPreferences {

    /**動作しているPeerCast。localhostまたは192.168.x.x*/
    abstract var peerCastUrl: Uri

    /**表示順*/
    abstract var displayOrder: YpDisplayOrder

    /**NGは非表示か、NGと表示するか*/
    abstract val isNgHidden: Boolean

    /**WMV,FLVなどのタイプでPecaPlayViewerが有効か*/
    abstract fun isViewerEnabled(type: String): Boolean

    /**通知するか*/
    abstract var isNotificationEnabled: Boolean

    /**通知音*/
    abstract var notificationSoundUrl: Uri?

    /**通知済み新着のChannel-Id*/
    abstract var notificationNewlyChannelsId: List<String>

}
