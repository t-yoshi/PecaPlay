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


    /**ナイトモードUIか*/
    const val EXTRA_NIGHT_MODE = "night-mode"

}