package org.peercast.pecaplay

/**
 *
 * プレーヤー側に渡すインテントの定義
 * @version 5.1
 */
object PecaPlayIntent {

    /**(PecaPlay内) 新着を表示する*/
    internal const val EXTRA_IS_NEWLY = "newly"


    /**PecaPlayが発行したインテントであるか (Boolean)*/
    const val EXTRA_IS_LAUNCH_FROM_PECAPLAY = "launch-from-pecaplay"

    /**
     * チャンネル　コンタクトURL (String)
     * @see org.peercast.core.ChannelInfo.EXTRA_CONTACT_URL
     * */
    const val EXTRA_CONTACT_URL = "contact"

    /**
     * チャンネル　名 (String)
     * @see org.peercast.core.ChannelInfo.EXTRA_NAME
     * */
    const val EXTRA_NAME = "name"

    /**
     * チャンネル　詳細 (String)
     * @see org.peercast.core.ChannelInfo.EXTRA_DESCRIPTION
     * */
    const val EXTRA_DESCRIPTION = "description"

    /**
     * チャンネル　コメント (String)
     * @see org.peercast.core.ChannelInfo.EXTRA_COMMENT
     * */
    const val EXTRA_COMMENT = "comment"

    /**ナイトモードUIか*/
    const val EXTRA_NIGHT_MODE = "night-mode"

}