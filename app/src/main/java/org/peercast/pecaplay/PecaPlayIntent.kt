package org.peercast.pecaplay

/**
 *
 * プレーヤー側に渡すインテントの定義
 * @version 5.1
 */
object PecaPlayIntent {

    /**(PecaPlay内) 通知済みの新着を表示する*/
    internal const val EXTRA_IS_NOTIFICATED = "notificated"


    /**PecaPlayが発行したインテントであるか (Boolean)*/
    const val EXTRA_IS_LAUNCH_FROM_PECAPLAY = "launch-from-pecaplay"


    /**ナイトモードUIか*/
    const val EXTRA_NIGHT_MODE = "night-mode"

}