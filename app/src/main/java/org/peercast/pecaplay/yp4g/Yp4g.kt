package org.peercast.pecaplay.yp4g

import android.arch.persistence.room.Embedded
import android.arch.persistence.room.Ignore
import android.net.Uri
import android.support.annotation.MainThread
import android.text.Html
import com.googlecode.kanaxs.KanaUtil
import org.peercast.pecaplay.ObjectUtils
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.Path
import org.simpleframework.xml.Root
import java.lang.Exception
import java.util.*
import java.util.regex.Pattern


private val UNESCAPE_HTML: (String) -> String = { Html.fromHtml(it).toString() }
private val PAT_DESCRIPTION = Regex("""( - )?<(\dM(bps)? )?(Over|Open|Free)>$""")
private val PREPARE_DESCRIPTION: (String) -> String = {
    UNESCAPE_HTML(it).replace(PAT_DESCRIPTION, "")
}
private val VALIDATE_CHANNEL_ID: (String) -> String = {
    it.also {
        if (it.length != 32)
            throw IllegalArgumentException("ch.id.size != 32")
    }
}

private val CONVERT_INT: (String) -> Int = {
    it.toInt()
}


enum class Yp4gColumn(val convert: (String) -> Any = { it }) {
    Name(UNESCAPE_HTML), //#1
    Id(VALIDATE_CHANNEL_ID),
    Ip(),
    Url(),
    Genre(UNESCAPE_HTML), //#5
    Description(PREPARE_DESCRIPTION),
    Listeners(CONVERT_INT),
    Relays(CONVERT_INT),
    Bitrate(CONVERT_INT),
    Type(UNESCAPE_HTML),//#10
    TrackArtist(UNESCAPE_HTML),
    TrackAlbum(UNESCAPE_HTML),
    TrackTitle(UNESCAPE_HTML),
    TrackContact(UNESCAPE_HTML),
    NameUrl(), //#15
    Age(),
    Status(UNESCAPE_HTML),
    Comment(UNESCAPE_HTML),
    Direct(),
    // __END_OF_index_txt num=19

    YpName({ throw RuntimeException() }),
    YpUrl({ throw RuntimeException() })
}

open class Yp4gChannel {
    data class Field(
            val name: String, // #1
            val id: String,
            val ip: String,
            val url: Uri,
            val genre: String, // #5
            val description: String,
            val listeners: Int,
            val relays: Int,
            val bitrate: Int,
            val type: String,// #10
            val trackArtist: String,
            val trackAlbum: String,
            val trackTitle: String,
            val trackContact: String,
            val nameUrl: Uri,//#15
            val age: String,
            val status: String,
            val comment: String,
            val direct: String,
            // __END_OF_index_txt

            val ypName: String,//YP名 #20
            val ypUrl: Uri
    )

    @Embedded
    lateinit var yp4g: Field

    open val isEnabled get() = !isEmptyId

    val isEmptyId get() = yp4g.id == EMPTY_ID


    fun playlist(peca: Uri): Uri =
            Uri.parse("http://${peca.host}:${peca.port}/pls/${yp4g.id}?tip=${yp4g.ip}")

    fun chatUrl(): Uri {
        return Uri.withAppendedPath(yp4g.ypUrl, "chat.php?cn=${yp4g.name}")
    }

    fun stream(peca: Uri): Uri {
        val t = yp4g.type.toLowerCase()
        return when {
            t == "wmv" ->
                Uri.parse("mmsh://${peca.host}:${peca.port}/stream/${yp4g.id}.wmv?tip=${yp4g.ip}")

            t in arrayOf("flv", "webm", "mkv") ->
                Uri.parse("http://${peca.host}:${peca.port}/stream/${yp4g.id}.$t?tip=${yp4g.ip}")

            else -> playlist(peca)
        }
    }

    fun statisticsUrl(): Uri {
        return Uri.withAppendedPath(yp4g.ypUrl, "getgmt.php?cn=${yp4g.name}")
    }

    override fun toString(): String = "Yp4gChannel [${yp4g.name},${yp4g.id}]"

    /**
     * IdとNameが同じならtrue
     */
    fun equalsIdName(other: Yp4gChannel): Boolean {
        return yp4g.name == other.yp4g.name && yp4g.id == other.yp4g.id
    }

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equals(this, other, { yp4g.name }, { yp4g.id })
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCode(this::class, yp4g.name, yp4g.id)
    }

    @delegate:Ignore
    val ageAsMinutes by lazy {
        val m = RE_AGE.matcher(yp4g.age)
        if (m.find())
            m.group(1).toInt() * 60 + m.group(2).toInt()
        else
            0
    }

    @get:MainThread
    @delegate:Ignore
    val searchText by lazy(LazyThreadSafetyMode.NONE) {
        with(yp4g) {
            toNormalizedJapanese("$name $comment $description")
        }
    }

    companion object {
        private const val TAG = "Yp4gChannel"

        const val EMPTY_ID = "00000000000000000000000000000000" // 0{32}

        private val RE_AGE = Pattern.compile("(\\d+):(\\d\\d)\\s*$")
    }

}

private object YpComparators {

    //複数のコンパレータをつなげる
    fun <T> chained(vararg cmps: Comparator<T>): Comparator<T> {
        return Comparator { a, b ->
            cmps.map {
                it.compare(a, b)
            }.firstOrNull { it != 0 } ?: 0
        }
    }

    //お知らせを常に下に持っていく
    val CMP_NOT_CHANNELS: Comparator<Yp4gChannel> = Comparator { a, b ->
        val infoA = a.yp4g.listeners < -1
        val infoB = b.yp4g.listeners < -1
        if (infoA && infoB)
            0
        else
            infoA.compareTo(infoB)
    }

    //リスナー数で比較
    val CMP_LISTENERS: Comparator<Yp4gChannel> = Comparator { a, b ->
        a.yp4g.listeners.compareTo(b.yp4g.listeners)
    }

    val CMP_LISTENERS_REV = Collections.reverseOrder(CMP_LISTENERS)!!

    //Ch名で比較
    val CMP_NAME: Comparator<Yp4gChannel> = Comparator { a, b ->
        a.yp4g.name.compareTo(b.yp4g.name)
    }

    //配信時間で比較
    val CMP_AGES: Comparator<Yp4gChannel> = Comparator { a, b ->
        a.ageAsMinutes.compareTo(b.ageAsMinutes)
    }

    val CMP_AGES_REV = Collections.reverseOrder(CMP_AGES)!!
}

enum class YpOrder(cmp: Comparator<Yp4gChannel>) {
    LISTENERS_DESC(YpComparators.CMP_LISTENERS_REV),
    LISTENERS_ASC(YpComparators.CMP_LISTENERS),
    AGE_DESC(YpComparators.CMP_AGES_REV),
    AGE_ASC(YpComparators.CMP_AGES);

    val comparator = YpComparators.chained(
            YpComparators.CMP_NOT_CHANNELS,
            cmp, YpComparators.CMP_LISTENERS_REV, YpComparators.CMP_AGES_REV,
            YpComparators.CMP_NAME
    )

    companion object {
        private val DEFAULT = LISTENERS_DESC

        fun fromName(name: String) =
                values().firstOrNull {
                    it.name.equals(name, true)
                } ?: DEFAULT

        fun fromOrdinal(o: Int) = values().getOrNull(o) ?: DEFAULT
    }
}

/**
YP4G仕様
http://mosax.sakura.ne.jp/yp4g/fswiki.cgi?page=YP4G%BB%C5%CD%CD
 */
@Root(name = "yp4g")
class Yp4gConfig {
    @field:[Path("yp") Attribute]
    var name = "(none)"
        private set

    @field:Element
    var host = Host()
        private set

    @field:Element
    var uptest = UpTest()
        private set

    @field:Element
    var uptest_srv = UpTestServer()
        private set

    class Host {
        @field:Attribute
        var ip: String = ""
            private set

        @field:Attribute
        private var port_open = 0

        @field:Attribute
        var speed = 0
            private set

        @field:Attribute
        private var over = 0

        val isPortOpen get() = port_open == 1
        val isOver get() = over == 1

        override fun toString(): String {
            return "ip=$ip, isPortOpen=$isPortOpen, speed=$speed, isOver=$isOver"
        }
    }

    class UpTest {
        @field:Attribute
        private var checkable = 0

        @field:Attribute
        var remain = 0
            private set

        val isCheckable get() = checkable == 1

        override fun toString(): String {
            return "isCheckable=$isCheckable, remain=$remain"
        }
    }

    class UpTestServer {
        @field:Attribute
        var addr = ""
            private set

        @field:Attribute
        var port = 0
            private set

        @field:Attribute
        var `object` = ""
            private set

        /**KBytes */
        @field:Attribute(name = "post_size")
        var postSize = 0
            private set

        @field:Attribute
        var limit = 0
            private set

        @field:Attribute
        var interval = 0
            private set

        @field:Attribute
        private var enabled = 0

        val isEnabled get() = enabled == 1

        override fun toString(): String {
            return "addr=$addr, port=$port, object=$`object`, postSize=$postSize, limit=$limit, interval=$interval, isEnabled=$isEnabled"
        }
    }

    companion object {
        val NONE = Yp4gConfig()
    }

    override fun toString(): String {
        return "Yp4gConfig(name=$name, host=$host, uptest=[$uptest], uptest_srv=[$uptest_srv])"
    }
}

private val TAG = "Yp4g"

/*
class Yp4gChannelExtra(val channel: Channel) {
    /**
     * 再生できるか否か
     */
    var isSchedulable: Boolean = channel.id != Channel.EMPTY_ID
        internal set(value) {
            field = value && channel.id != Channel.EMPTY_ID
        }

    //過去3回の更新時
    private val _lastLoaded = arrayOfNulls<Timestamp>(3)

    val ageAsMinutes by lazy {
        with(PAT_AGE.matcher(channel.age)) {
            if (find())
                group(1).toIntFlag() * 60 + group(2).toIntFlag()
            else
                0
        }
    }

    val searchText by lazy {
        toNormalizedJapanese(
                with(channel) {
                    "$name $comment $description"
                }
        )
    }

    //var tag: Any? = null
//    @Deprecated("")
//    var isNotificated: Boolean = false // 4)

    /**
     * ロードされた時刻のリスト。

     * @return 最初のエレメントは直近のロード時刻。最後のエレメントは最古のロード時刻。<br></br>
     * * 履歴から生成されたときempty。
     */
    val lastLoaded: List<Timestamp>
        get () = _lastLoaded.filterNotNull()

    internal fun putLastLoaded(t0: Long?, t1: Long?, t2: Long?) {
        _lastLoaded[0] = t0?.let { Timestamp(it) }
        _lastLoaded[1] = t1?.let { Timestamp(it) }
        _lastLoaded[2] = t2?.let { Timestamp(it) }
    }

    companion object {
        private val PAT_AGE = Pattern.compile("(\\d+):(\\d\\d)\\s*$")


    }
}
*/

/**
 * 検索用:　小文字、半角英数、 ひらがな化
 */
fun toNormalizedJapanese(text: String): String =
        text.let {
            var s = it.toLowerCase(Locale.JAPANESE)
            s = KanaUtil.toHanalphCase(s)
            KanaUtil.toHiraganaCase(s)
        }


class Yp4gFormatException(msg: String, cause: Exception? = null)
    : IllegalArgumentException(msg, cause)
