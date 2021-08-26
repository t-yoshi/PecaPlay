package org.peercast.pecaviewer.chat.net

/**疑似コネクション*/
class MockBbsConnection(private val url: String) : IBoardThreadConnection {
    override val info = object : IThreadInfo {
        override val url = this@MockBbsConnection.url
        override val title = url
        override val board = object : IBoardInfo {
            override val url = this@MockBbsConnection.url
            override val title = url
        }
        override val creationDate: String = ""
        override val numMessages = 1
        override val isPostable = false
    }

    override suspend fun loadThreads(): List<IThreadInfo> = listOf(info)

    override suspend fun openThreadConnection(threadInfo: IThreadInfo): IBoardThreadConnection {
        return this
    }

    override suspend fun loadMessages(): List<IMessage> {
        return listOf(
            BbsMessage(info, 1, "", "", "", "Connection error:\n$url", "")
        )
    }
}

abstract class ConnectionFactory {
    /**
     *指定のURLを開き、 [IBoardConnection] を作成する。
     * 正しくないURLなど、作成できない場合はnull。
     * @throws none
     * */
    abstract suspend operator fun invoke(url: String): IBoardConnection?
}

suspend fun openBoardConnection(url: String): IBoardConnection {
    listOf(
        ShitarabaConnectionFactory,
        StampCastConnectionFactory,
        ZeroChannelConnectionFactory
    ).forEach { f ->
        f(url)?.let { return it }
    }
    return MockBbsConnection(url)
}

