package org.peercast.pecaviewer.chat.net


import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit


private class ShitarabaBoardInfo(m: Map<String, String>) : BaseBbsBoardInfo() {
    val dir = m["DIR"] ?: "" //=game
    val bbsNumber = m["BBS"] ?: "" //=59608
    val categoryName = m["CATEGORY"] ?: ""  //=ゲーム/囲碁/将棋
    val isAdult = m["BBS_ADULT"] != "0" //=0
    val numThreadStop = m["BBS_THREAD_STOP"]?.toIntOrNull() ?: 1000
    val nonameName = m["BBS_NONAME_NAME"] ?: ""  //=俺より強い名無しに会いにいく＠転載禁止
    val deleteName = m["BBS_DELETE_NAME"] ?: ""  //=＜削除＞
    override val title = m["BBS_TITLE"] ?: ""  //=ウメハラ総合板四代目(転載禁止)
    val comment = m["BBS_COMMENT"] ?: ""  //=ウメスレ
    val error = m["ERROR"] ?: ""
    override val url = m["TOP"] ?: "https://jbbs.shitaraba.net/$dir/$bbsNumber/"

    override fun toString(): String {
        return "ShitarabaBoardInfo(dir='$dir', bbsNumber='$bbsNumber', categoryName='$categoryName', isAdult=$isAdult, numThreadStop=$numThreadStop, nonameName='$nonameName', deleteName='$deleteName', title='$title', comment='$comment', error='$error')"
    }
}


private class ShitarabaThreadInfo(
    override val board: ShitarabaBoardInfo,
    datPath: String, title: String,
) : BaseBbsThreadInfo(datPath, title) {
    override val url =
        "https://jbbs.shitaraba.net/bbs/read.cgi/${board.dir}/${board.bbsNumber}/${number}/"
    override val isPostable = numMessages < board.numThreadStop
}

private val CC_MAX_STALE_1DAY = CacheControl.Builder()
    .maxStale(1, TimeUnit.DAYS)
    .build()

private val CC_MAX_STALE_10SEC = CacheControl.Builder()
    .maxStale(10, TimeUnit.SECONDS)
    .build()

private class ShitarabaBoardConnection(
    val client: BbsClient,
    override val info: ShitarabaBoardInfo,
) : IBoardConnection {
    override suspend fun loadThreads(): List<ShitarabaThreadInfo> {
        val req = Request.Builder()
            .url("https://jbbs.shitaraba.net/${info.dir}/${info.bbsNumber}/subject.txt")
            .cacheControl(CC_MAX_STALE_10SEC)
            .build()
        val threads = client.parseSubjectText(req, ",") { path, title ->
            ShitarabaThreadInfo(info, path, title)
        }
        if (threads.size >= 2 && threads.first() == threads.last()) {
            //最初と最後に同じスレッドがあるので除く
            return threads.subList(0, threads.size - 1)
        }
        return threads
    }

    override suspend fun openThreadConnection(threadInfo: IThreadInfo): IBoardThreadConnection? {
        if (threadInfo !is ShitarabaThreadInfo || threadInfo.board != info) {
            Timber.w("wrong threadInfo: $threadInfo but board=$info")
            return null
        }
        return ShitarabaBoardThreadConnection(this, threadInfo)
    }

    companion object {
        private suspend fun loadBoardInfo(
            client: BbsClient,
            boardDir: String,
            boardNumber: String,
        ): ShitarabaBoardInfo {
            val req = Request.Builder()
                .url("https://jbbs.shitaraba.net/bbs/api/setting.cgi/$boardDir/$boardNumber/")
                .cacheControl(CC_MAX_STALE_1DAY)
                .build()

            val m = HashMap<String, String>(15)
            m.putAll(
                client.parseText(req, "=", 2) { it[0] to it[1] }
            )
            return ShitarabaBoardInfo(m)
        }

        private val EUC_JP = Charset.forName("euc-jp")

        suspend fun open(dir: String, bbsNumber: String): ShitarabaBoardConnection {
            val client = BbsClient(EUC_JP)
            val boardInfo = loadBoardInfo(client, dir, bbsNumber)
            return ShitarabaBoardConnection(client, boardInfo)
        }
    }
}

private class ShitarabaBoardThreadConnection(
    private val base: ShitarabaBoardConnection,
    override val info: ShitarabaThreadInfo,
) : IBoardConnection by base, IBoardThreadConnection, IBoardThreadPoster {

    override suspend fun loadMessages(): List<BbsMessage> {
        val result = ArrayList<BbsMessage>(1000)
        suspend fun execRequest(url: String, cc: CacheControl) {
            val req = Request.Builder()
                .url(url)
                .cacheControl(cc).build()
            base.client.parseText(req, "<>", 7) { a ->
                BbsMessage(info, a[0].toIntOrNull() ?: 0, a[1], a[2], a[3], a[4], a[5])
            }.let(result::addAll)
        }

        val rawUrl =
            "https://jbbs.shitaraba.net/bbs/rawmode.cgi/${info.board.dir}/${info.board.bbsNumber}/${info.number}/"
        try {
            //2時間以内のキャッシュがあれば
            execRequest(
                rawUrl,
                CacheControl.Builder().maxStale(2, TimeUnit.HOURS).build()
            )
            if (result.isEmpty())
                throw BbsClient.UnsatisfiableRequestException("result.isEmpty()")
            //最新レスを取得して追加
            val n = result.last().number + 1
            if (n < info.board.numThreadStop)
                execRequest("$rawUrl$n-", FORCE_NETWORK_NO_STORE)
        } catch (e: BbsClient.UnsatisfiableRequestException) {
            //キャッシュは古かったので.datを再取得
            execRequest(rawUrl, CacheControl.FORCE_NETWORK)
        }

        result.lastOrNull()?.let { m ->
            //レス数を更新
            info.numMessages = m.number
        }

        return result
    }

    override suspend fun postMessage(m: PostMessage): CharSequence {
        val body = FormBody.Builder()
            .addEncoded("DIR", info.board.dir)
            .addEncoded("BBS", info.board.bbsNumber)
            .addEncoded("KEY", info.number)
            .addEncoded("NAME", m.name.eucjp())
            .addEncoded("MAIL", m.mail.eucjp())
            .addEncoded("MESSAGE", m.body.eucjp())
            .addEncoded("SUBMIT", "書き込む".eucjp())
            .build()
        val req = Request.Builder()
            .url("https://jbbs.shitaraba.net/bbs/write.cgi")
            .header("Cookie", "name=${m.name.eucjp()}; mail=${m.mail.eucjp()}")
            .header(
                "Referer",
                "https://jbbs.shitaraba.net/${info.board.dir}/${info.board.bbsNumber}/"
            )
            .cacheControl(FORCE_NETWORK_NO_STORE)
            .post(body)
            .build()
        return base.client.post(req)
    }

    companion object {
        private fun String.eucjp() = URLEncoder.encode(this, "euc-jp")
        private val FORCE_NETWORK_NO_STORE = CacheControl.Builder().noCache().noStore().build()

        suspend fun open(
            dir: String,
            bbsNumber: String,
            threadNumber: String,
        ): ShitarabaBoardThreadConnection {
            val base = ShitarabaBoardConnection.open(dir, bbsNumber)
            val threadInfo = base.loadThreads().firstOrNull {
                it.number == threadNumber
            } ?: throw IOException("threadNumber $threadNumber is not found.")
            return ShitarabaBoardThreadConnection(base, threadInfo)
        }
    }
}

object ShitarabaConnectionFactory : ConnectionFactory() {
    /**[boardDir, boardNumber, threadNumber]*/
    private val RE_URL =
        """^https?://jbbs\.(?:shitaraba\.net|livedoor\.jp)/(?:bbs/(?:read|subject)\.cgi/)?(\w+)/(\d+)(?:/(\d+))?""".toRegex()

    override suspend fun invoke(url: String): IBoardConnection? {
        val a = RE_URL.find(url)?.groupValues ?: return null
        return if (a[3].isEmpty())
            ShitarabaBoardConnection.open(a[1], a[2])
        else
            ShitarabaBoardThreadConnection.open(a[1], a[2], a[3])
    }
}

