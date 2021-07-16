package org.peercast.pecaviewer.chat.net

interface IBrowsable {
    /**ブラウザで開くことができるURL*/
    val url: String
}

/**この掲示板の情報*/
interface IBoardInfo : IBrowsable {
    val title: String
}

/**このスレッドの情報*/
interface IThreadInfo : IBoardInfo {
    val board: IBoardInfo
    val creationDate: String

    /**レス数 不明(-1)*/
    val numMessages: Int
    val isPostable: Boolean
}

interface IMessage {
    /**スレッド*/
    val threadInfo: IThreadInfo?

    /**レス番号*/
    val number: Int

    /**名前*/
    val name: CharSequence

    /**メールアドレス*/
    val mail: CharSequence

    /**日付*/
    val date: CharSequence

    /**
     * 本文
     * @see PopupSpan
     * @see ClickableSpan
     * @see ThumbnailSpan
     * */
    val body: CharSequence

    /**id*/
    val id: CharSequence
}


data class PostMessage(
    override val name: String,
    override val mail: String,
    override val body: String
) : IMessage {
    override val threadInfo: IThreadInfo? = null
    override val number: Int = 0
    override val date: String = ""
    override val id: String = ""
}

/**掲示板への接続*/
interface IBoardConnection {
    val info: IBoardInfo

    /**スレッドを取得する
     * @throws java.io.IOException
     * */
    suspend fun loadThreads(): List<IThreadInfo>

    /**掲示板スレッドへの接続を開く
     * @return null 適切なthreadInfoでない
     * @throws java.io.IOException
     * */
    suspend fun openThreadConnection(threadInfo: IThreadInfo): IBoardThreadConnection?
}

/**掲示板スレッドへの書き込み*/
interface IBoardThreadPoster {
    val info: IThreadInfo

    /**レスを送信する
     * @return 書き込み結果を示す文字列
     * @throws java.io.IOException
     * */
    suspend fun postMessage(m: PostMessage): CharSequence
}

/**掲示板スレッドへの接続と書き込み*/
interface IBoardThreadConnection : IBoardConnection {
    override val info: IThreadInfo

    /**レスを取得する
     * @throws java.io.IOException
     * */
    suspend fun loadMessages(): List<IMessage>
}

val IBoardInfo.boardTopTitle: String
    get() = when (this) {
        is IThreadInfo -> board.title
        else -> title
    }

val IBoardInfo.boardTopUrl: String
    get() = when (this) {
        is IThreadInfo -> board.url
        else -> url
    }