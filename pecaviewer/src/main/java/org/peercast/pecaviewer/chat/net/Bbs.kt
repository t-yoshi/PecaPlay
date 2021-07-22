package org.peercast.pecaviewer.chat.net

import android.text.Spannable
import android.text.style.URLSpan
import androidx.core.text.parseAsHtml
import androidx.core.text.set
import androidx.core.text.toSpannable
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.core.io.Square
import org.peercast.pecaplay.core.io.await
import org.peercast.pecaviewer.chat.adapter.PopupSpan.Companion.applyPopupSpanForAnchors
import org.peercast.pecaviewer.chat.net.BbsUtils.applyUrlSpan
import org.peercast.pecaviewer.chat.net.BbsUtils.stripHtml
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailSpan.Companion.applyThumbnailSpan
import org.peercast.pecaviewer.util.DateUtils
import org.unbescape.html.HtmlEscape
import timber.log.Timber
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class BbsClient(val defaultCharset: Charset) : KoinComponent {
    protected val square by inject<Square>()

    suspend fun <T : IThreadInfo> parseSubjectText(
        req: Request, delimiter: String,
        f: (path: String, title: String) -> T,
    ): List<T> {
        return parseText(req, delimiter, 2) { f(it[0], it[1]) }
    }

    suspend fun <T : Any> parseText(
        req: Request, delimiter: String, limit: Int,
        f: (a: List<String>) -> T,
    ): List<T> {
        return readStream(req) { lines ->
            lines.splitLines(delimiter, limit).map(f).toList()
        }
    }

    private suspend fun <T> readStream(req: Request, f: (Sequence<String>) -> T): T {
        return square.okHttpClient.newCall(req).await { res ->
            if (res.code == 504)
                throw UnsatisfiableRequestException(res.message)

            val body = res.body ?: throw IOException("body returned null.")
            val cs = body.contentType()?.charset() ?: defaultCharset
            body.byteStream().reader(cs).useLines(f)
        }
    }

    /**504: ローカルキャッシュが期限切れである*/
    class UnsatisfiableRequestException(msg: String) : IOException(msg)

    suspend fun post(req: Request): CharSequence {
        val ret = square.okHttpClient.newCall(req).await { res ->
            val body = res.body ?: throw IOException("body returned null.")
            val cs = body.contentType()?.charset() ?: defaultCharset
            body.byteStream().reader(cs).readText()
        }
        return ret.stripHtml().trim().replace(RE_SPACE, " ")
    }

    companion object {
        private fun Sequence<String>.splitLines(
            delimiters: String,
            limit: Int,
        ): Sequence<List<String>> {
            return mapIndexedNotNull { i, line ->
                line.split(delimiters, limit = limit).let { a ->
                    if (a.size == limit)
                        return@let a
                    Timber.e("limit is $limit but ${a.size}. #${i + 1}: $line")
                    null
                }
            }
        }

        private val RE_SPACE = """[\s　]+""".toRegex()
    }
}


abstract class BaseBbsBoardInfo : IBoardInfo {
    override fun hashCode(): Int {
        return Objects.hash(javaClass, url)
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseBbsBoardInfo &&
                other.javaClass == javaClass &&
                other.url == url
    }
}


abstract class BaseBbsThreadInfo(
    datPath: String, title_: String,
) : IThreadInfo {

    final override val title = HtmlEscape.unescapeHtml(
        title_.substringBeforeLast('(')
    ).trimEnd()

    //XXXX.cgi | XXXX.dat
    val number: String = datPath.substringBefore(".") //.toIntOrNull() ?: 0

    final override val creationDate = synchronized(DATE_FORMAT) {
        DATE_FORMAT.format(
            Date((number.toIntOrNull() ?: 0) * 1000L)
        )
    }

    //var: レス取得時に変更できるように
    final override var numMessages = RE_NUM_MESSAGES.find(title_)?.groupValues?.let {
        it[1].toIntOrNull()
    } ?: 0

    override fun hashCode(): Int {
        return Objects.hash(javaClass, board, number)
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseBbsThreadInfo &&
                other.javaClass == javaClass &&
                board == other.board &&
                number == other.number
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(title='$title', number='$number', creationDate='$creationDate', numMessages=$numMessages)"
    }

    companion object {
        private val RE_NUM_MESSAGES = """\((\d+)\)$""".toRegex()
        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)
    }
}

open class BbsMessage(
    final override val threadInfo: IThreadInfo,
    final override val number: Int,
    name: String,
    mail: String,
    final override val date: CharSequence,
    body: String,
    final override val id: CharSequence,
) : IMessage, IBrowsable {

    final override val name: CharSequence = name.parseAsHtml()
    final override val mail: CharSequence = mail.parseAsHtml()
    final override val body: CharSequence =
        //(body + TEST_TEXT).stripHtml().toSpannable()
        body.stripHtml().toSpannable()
            .applyPopupSpanForAnchors() // PopupSpanを適用し、>123のようなアンカーでポップアップ
            .applyUrlSpan() // URLSpanを適用し、リンクを動作させる
            .applyThumbnailSpan() //ThumbnailSpanを適用し、サムネを生成する

    override val url = "${threadInfo.url}$number"

    val timeInMillis = DateUtils.parseData(date)

    override fun toString(): String {
        return "$number: ${body.take(24)}"
    }

    override fun equals(other: Any?): Boolean {
        return other is BbsMessage &&
                other.javaClass == javaClass &&
                other.url == url
    }

    override fun hashCode(): Int {
        return Objects.hash(javaClass, url)
    }
}

object BbsUtils {
    private val RE_REMOVE_TAG = """(?is)<(script|style)[ >].+?</\1>""".toRegex()
    private val RE_URL = """h?ttps?://[\w\-~/_.$}{#%,:@?&|=+]+""".toRegex()

    fun CharSequence.stripHtml(): CharSequence {
        return RE_REMOVE_TAG.replace(this, "")
            .parseAsHtml().toString()
    }

    /**URLSpanを適用する*/
    fun Spannable.applyUrlSpan(): Spannable {
        RE_URL.findAll(this).forEach { mr ->
            var u = mr.groupValues[0]
            if (u.startsWith("ttp"))
                u = "h$u"
            this[mr.range.first, mr.range.last + 1] = URLSpan(u)
            //Timber.d("${mr.range}: $u")
        }
        return this
    }

    const val TEST_TEXT = """
             "https://media2.giphy.com/media/xreCEnteawblu/giphy.gif?cid=ecf05e47scyg0bt1ljd58r7kj4xkcifs4x5c92pf5bwfhygv&rid=giphy.gif"),
            "https://i.giphy.com/media/2igz2N2bac1Wg/giphy.webp"),
            ttps://i.pinimg.com/originals/a7/dc/70/a7dc706832d1f818a3cb9d2202eb25cf.gif"),
            ("https://upload.wikimedia.org/wikipedia/commons/9/9a/PNG_transparency_demonstration_2.png"),
            https://www.youtube.com/watch?v=DsYdPQ1igvM
            https://www.nicovideo.jp/watch/sm9
            https://file-examples-com.github.io/uploads/2017/10/file_example_JPG_1MB.jpg
             >>1
        """
}

