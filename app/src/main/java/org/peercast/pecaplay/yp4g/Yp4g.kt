package org.peercast.pecaplay.yp4g

import android.net.Uri
import androidx.room.Embedded
import androidx.room.Ignore
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.Path
import org.simpleframework.xml.Root
import org.unbescape.html.HtmlEscape
import java.util.*
import kotlin.collections.HashMap


class Yp4gFormatException(msg: String) : IllegalArgumentException(msg)


enum class Yp4gColumn(val convert: (String) -> String = Converter.None) {
    Name(Converter.UnescapeHtml), // #0
    Id(Converter.ChannelId),
    Ip(),
    Url(),
    Genre(Converter.UnescapeHtml),
    Description(Converter.Description),//#5
    Listeners(Converter.Number),
    Relays(Converter.Number),
    Bitrate(Converter.Number),
    Type(Converter.UnescapeHtml),
    TrackArtist(Converter.UnescapeHtml),//#10
    TrackAlbum(Converter.UnescapeHtml),
    TrackTitle(Converter.UnescapeHtml),
    TrackContact(Converter.UnescapeHtml),
    NameUrl(),
    Age(), //#15
    Status(Converter.UnescapeHtml),
    Comment(Converter.UnescapeHtml),
    Direct(),
    // __END_OF_index_txt num=19

    YpName(),
    YpUrl(); //#20

    companion object {
        private object Converter {
            val None: (String) -> String = { it }
            val UnescapeHtml: (String) -> String = { HtmlEscape.unescapeHtml(it) }
            val Number: (String) -> String = { it.toIntOrNull() ?: throw Yp4gFormatException("not number '$it'"); it }
            val ChannelId: (String) -> String = {
                if (it.length != 32)
                    throw Yp4gFormatException("ch.id.size != 32")
                it
            }
            val Description: (String) -> String = {
                UnescapeHtml(it).replace(PAT_DESCRIPTION, "")
            }
            private val PAT_DESCRIPTION = Regex("""( - )?<(\dM(bps)? )?(Over|Open|Free)>$""")
        }
    }
}


data class Yp4gField(
    val name: String, //#0
    val id: String,
    val ip: String,
    val url: Uri,
    val genre: String,
    val description: String,// #5
    val listeners: Int,
    val relays: Int,
    val bitrate: Int,
    val type: String,
    val trackArtist: String,// #10
    val trackAlbum: String,
    val trackTitle: String,
    val trackContact: String,
    val nameUrl: Uri,
    val age: String,// #15
    val status: String,
    val comment: String,
    val direct: String,
    // __END_OF_index_txt

    val ypName: String,
    val ypUrl: Uri// #20
)


/**
 * YP4G/index.txtのChannel情報です。
 */
abstract class YpChannel {
    @Embedded
    lateinit var yp4g: Yp4gField

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

    override fun toString(): String = "${javaClass.simpleName} [${yp4g.name},${yp4g.id}]"

    /**
     * IdとNameが同じならtrue
     */
    fun equalsIdName(other: YpChannel): Boolean {
        return yp4g.name == other.yp4g.name && yp4g.id == other.yp4g.id
    }

    override fun equals(other: Any?): Boolean {
        return other is YpChannel &&
                other.javaClass === javaClass &&
                equalsIdName(other)
    }

    override fun hashCode(): Int {
        return Objects.hash(javaClass, yp4g.name, yp4g.id)
    }

    @Ignore
    private val tag = HashMap<String, Any?>()

    /**追加プロパティのキャッシュ 検索文など*/
    fun <T> extTag(key: String, putValue: YpChannel.() -> T?): T? {
        @Suppress("unchecked_cast")
        return tag.getOrPut(key) { this.putValue() } as T?
    }

    companion object {
        private const val TAG = "YpChannel"

        const val EMPTY_ID = "00000000000000000000000000000000" // 0{32}
    }

}

typealias YpChannelSelector = (YpChannel) -> Boolean


enum class YpDisplayOrder(cmp: Comparator<YpChannel>) {
    LISTENERS_DESC(Comparators.LISTENERS_REV),
    LISTENERS_ASC(Comparators.LISTENERS),
    AGE_DESC(Comparators.AGE_REV),
    AGE_ASC(Comparators.AGE),
    NONE(Comparator { _, _ -> throw RuntimeException() });

    val comparator = Comparators.chained(
        Comparators.NOTICE,
        cmp, Comparators.LISTENERS_REV,
        Comparators.AGE_REV, Comparators.NAME
    )

    companion object {
        private val DEFAULT = LISTENERS_DESC

        fun fromName(name: String?) =
            values().firstOrNull {
                it.name.equals(name, true)
            } ?: DEFAULT

        fun fromOrdinal(o: Int) = values().getOrNull(o) ?: DEFAULT

        private object Comparators {
            //お知らせを常に下に持っていく
            val NOTICE = Comparator<YpChannel> { a, b ->
                val infoA = a.yp4g.listeners < -1
                val infoB = b.yp4g.listeners < -1
                if (infoA && infoB)
                    0
                else
                    infoA.compareTo(infoB)
            }

            //リスナー数で比較
            val LISTENERS = Comparator<YpChannel> { a, b ->
                a.yp4g.listeners.compareTo(b.yp4g.listeners)
            }

            val LISTENERS_REV = Collections.reverseOrder(LISTENERS)!!

            //Ch名で比較
            val NAME = Comparator<YpChannel> { a, b ->
                a.yp4g.name.compareTo(b.yp4g.name)
            }


            //配信時間で比較
            val AGE: Comparator<YpChannel> = Comparator { a, b ->
                a.ageAsMinutes.compareTo(b.ageAsMinutes)
            }

            val AGE_REV = Collections.reverseOrder(AGE)!!

            //複数のコンパレータをつなげる
            fun chained(vararg cmps: Comparator<YpChannel>): Comparator<YpChannel> {
                return Comparator { a, b ->
                    cmps.map {
                        it.compare(a, b)
                    }.firstOrNull { it != 0 } ?: 0
                }
            }
        }

        private val YpChannel.ageAsMinutes: Int
            get() = extTag("YpDisplayOrder#ageAsMinutes") {
                "(\\d+):(\\d\\d)\\s*$".toRegex().find(yp4g.age)
                    ?.let { m ->
                        m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                    }
            } ?: 0
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
        return "Yp4gConfig(name=$name, host=[$host], uptest=[$uptest], uptest_srv=[$uptest_srv])"
    }
}

private const val TAG = "Yp4g"



