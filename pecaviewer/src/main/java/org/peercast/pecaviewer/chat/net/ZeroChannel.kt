package org.peercast.pecaviewer.chat.net

import okhttp3.FormBody
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.*


private class ZeroChannelBoardInfo(
    val baseUrl: String, //ex. http://hibino.ddo.jp/bbs/
    val board: String,   //    peca
    m: Map<String, String>
) : BaseBbsBoardInfo() {
    override val url: String = "$baseUrl$board/" // http://hibino.ddo.jp/bbs/peca/
    override val title: String = m["BBS_TITLE"] ?: "??"
    val numResMax = m["BBS_RES_MAX"]?.toIntOrNull() ?: 1000
}

private class ZeroChannelThreadInfo(
    override val board: ZeroChannelBoardInfo,
    datPath: String, title: String
) : BaseBbsThreadInfo(datPath, title) {
    override val url =
        "${board.baseUrl}test/read.cgi/${board.board}/${number}/" //http://hibino.ddo.jp/bbs/test/read.cgi/peca/1582120664/
    override val isPostable = numMessages < board.numResMax
}

private class ZeroChannelBoardConnection(
    val client: BbsClient,
    override val info: ZeroChannelBoardInfo
) : IBoardConnection {
    override suspend fun loadThreads(): List<ZeroChannelThreadInfo> {
        val req = Request.Builder()
            .url("${info.baseUrl}${info.board}/subject.txt")
            .header("Cache-Control", "private, must-revalidate, max-age=5")
            .build()
        return client.parseSubjectText(req, "<>") { path, title ->
            ZeroChannelThreadInfo(info, path, title)
        }
    }

    override suspend fun openThreadConnection(threadInfo: IThreadInfo): IBoardThreadConnection? {
        if (threadInfo !is ZeroChannelThreadInfo || threadInfo.board != info) {
            Timber.w("wrong threadInfo: $threadInfo")
            return null
        }
        return ZeroChannelBoardThreadConnection(this, threadInfo)
    }

    companion object {
        private suspend fun loadBoardInfo(
            client: BbsClient,
            baseUrl: String,
            board: String
        ): ZeroChannelBoardInfo {
            val req = Request.Builder()
                .url("$baseUrl$board/SETTING.TXT")
                .header("Cache-Control", "private, must-revalidate, max-age=3600")
                .build()
            val m = HashMap<String, String>(25)
            m.putAll(
                client.parseText(req, "=", 2) { it[0] to it[1] }
            )
            return ZeroChannelBoardInfo(baseUrl, board, m)
        }

        private val SHIFT_JIS = Charset.forName("shift-jis")

        suspend fun open(baseUrl: String, board: String): ZeroChannelBoardConnection {
            val client = BbsClient(SHIFT_JIS)
            val boardInfo = loadBoardInfo(client, baseUrl, board)
            return ZeroChannelBoardConnection(client, boardInfo)
        }
    }
}

private class ZeroChannelBoardThreadConnection(
    private val base: ZeroChannelBoardConnection,
    override val info: ZeroChannelThreadInfo
) : IBoardConnection by base, IBoardThreadConnection, IBoardThreadPoster {

    override suspend fun loadMessages(): List<IMessage> {
        val req = Request.Builder()
            .url("${info.board.baseUrl}${info.board.board}/dat/${info.number}.dat")
            // If-Modified-Sinceを有効にする
            .header("Cache-Control", "private, must-revalidate, max-age=5")
            .build()
        var n = 0
        val result = base.client.parseText(req, "<>", 5) { a ->
            val date = a[2].substringBefore(" ID:")
            val id = a[2].substringAfter(" ID:", "")
            BbsMessage(info, ++n, a[0], a[1], date, a[3], id)
        }
        info.numMessages = n
        return result
    }

    override suspend fun postMessage(m: PostMessage): CharSequence {
        //bbs=[BOARD]&time=[POST_TIME]&FROM=[POST_NAME]&mail=[POST_MAIL]&MESSAGE=[POST_MESSAGE]
        val body = FormBody.Builder()
            .addEncoded("bbs", info.board.board)
            .addEncoded("time", "${System.currentTimeMillis() / 1000L}")
            .addEncoded("FROM", sjis(m.name))
            .addEncoded("mail", sjis(m.mail))
            .addEncoded("MESSAGE", sjis(m.body))
            .addEncoded("key", info.number)
            .addEncoded("submit", sjis("書き込む"))
            .build()

        //NOTE: UAはMozilla/5.0をつけること
        val req = Request.Builder()
            .url("${info.board.baseUrl}test/bbs.cgi")
            .post(body)
            .header("Cookie", "NAME=\"${sjis(m.name)}\"; MAIL=\"${sjis(m.mail)}\"")
            .header("Referer", "${info.board.baseUrl}${info.board.board}/")
            .header("User-Agent", "Mozilla/5.0")
            .header("Cache-Control", "no-store")
            .build()

        return base.client.post(req)
    }

    companion object {
        private fun sjis(s: String) = URLEncoder.encode(s, "shift-jis")
    }
}


object ZeroChannelConnectionFactory : ConnectionFactory() {
    //[baseUrl, boardName, threadNumber]
    private val RE_URL_1 = """^(https?://.+/)test/read\.cgi/(\w+)/(\d+)/?""".toRegex()

    //[baseUrl, boardName]
    private val RE_URL_2 = """^(https?://.+/)(?:[^/]+/)*(\w+)/?$""".toRegex()

    //[baseUrl, boardName, threadNumber or ""]
    private fun parseUrl(url: String): List<String>? {
        RE_URL_1.find(url)?.groupValues?.let {
            return it.drop(1)
        }
        RE_URL_2.find(url)?.groupValues?.let {
            return it.drop(1) + ""
        }
        Timber.d("invalid url: $url")
        return null
    }


    override suspend fun invoke(url: String): IBoardConnection? {
        val (baseUrl, board, threadNumber) = parseUrl(url) ?: return null
        try {
            val base = ZeroChannelBoardConnection.open(baseUrl, board)
            if (threadNumber.isEmpty())
                return base
            base.loadThreads().forEach { th ->
                if (th.number == threadNumber)
                    return base.openThreadConnection(th)
            }
            //過去ログ倉庫に
            Timber.w("thread not found: $url")
            return base
        } catch (e: IOException) {
            Timber.w(e, "connection failed: $url")
        }
        return null
    }
}

