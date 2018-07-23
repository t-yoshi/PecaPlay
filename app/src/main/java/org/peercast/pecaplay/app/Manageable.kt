package org.peercast.pecaplay.app

import android.arch.persistence.room.*
import android.net.Uri
import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize
import org.peercast.pecaplay.SquareUtils
import org.peercast.pecaplay.yp4g.Yp4gChannel
import org.peercast.pecaplay.yp4g.Yp4gColumn
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern


abstract class ManageableEntity : Parcelable {
    abstract val name: String
    abstract val isEnabled: Boolean

    final override fun hashCode() = javaClass.hashCode() * 31 + name.hashCode()

    final override fun equals(other: Any?): Boolean {
        return other is ManageableEntity &&
                javaClass == other.javaClass &&
                name == other.name
    }
}


/**
 * イエローページ
 * @version 50000
 */
@Entity(primaryKeys = ["name"])
@Parcelize
data class YellowPage(
        override val name: String,
        val url: String,
        @ColumnInfo(name = "enabled")
        override val isEnabled: Boolean = true) : ManageableEntity() {

    companion object {
        fun isValidUrl(u: String) : Boolean {
            return Uri.parse(u).run {
                scheme == "http" && !host.isNullOrEmpty() && path.endsWith("/")
            }
        }
    }
}

/**
 * お気に入り
 * @version 50000
 */
@Parcelize
@Entity(primaryKeys = ["name"])
@TypeConverters(Favorite.Converter::class)
data class Favorite(
        override val name: String,
        val pattern: String,
        val flags: Flags,
        @ColumnInfo(name = "enabled")
        override val isEnabled: Boolean = true) : ManageableEntity(), Parcelable {

    @Parcelize
    @JsonClass(generateAdapter = true)
    data class Flags(
            /**Ch名にマッチする*/
            val isName: Boolean = false,

            /**Ch詳細にマッチする*/
            val isDescription: Boolean = false,

            /**Chコメントにマッチする*/
            val isComment: Boolean = false,

            /**ChコンタクトURLにマッチする*/
            @Deprecated("")
            val isUrl: Boolean = false,

            /**Chジャンルにマッチする*/
            val isGenre: Boolean = false,

            /**NGである*/
            val isNG: Boolean = false,

            /**通知する対象*/
            val isNotification: Boolean = false,

            /** 完全一致のみ。 */
            val isExactMatch: Boolean = false,

            /**正規表現*/
            val isRegex: Boolean = false,

            /**  大文字小文字の違いを区別する */
            val isCaseSensitive: Boolean = false
    ) : Parcelable

    fun matches(ch: Yp4gChannel): Boolean {
        return true in if (flags.isRegex) {
            val reFlags = when (flags.isCaseSensitive) {
                true -> 0
                false -> Pattern.CASE_INSENSITIVE
            }
            val rePattern = Pattern.compile(pattern, reFlags)
            val reMatches: (Matcher) -> Boolean = when (flags.isExactMatch) {
                true -> {
                    { it.matches() }
                }
                false -> {
                    { it.find() }
                }
            }

            booleanArrayOf(
                    flags.isName && reMatches(rePattern.matcher(ch.yp4g.name)),
                    flags.isDescription && reMatches(rePattern.matcher(ch.yp4g.description)),
                    flags.isComment && reMatches(rePattern.matcher(ch.yp4g.comment)),
                    flags.isGenre && reMatches(rePattern.matcher(ch.yp4g.genre))
            )
        } else {
            val strMatches: (String) -> Boolean = when (flags.isExactMatch) {
                true -> {
                    { it.equals(pattern, !flags.isCaseSensitive) }
                }
                false -> {
                    { it.contains(pattern, !flags.isCaseSensitive) }
                }
            }

            booleanArrayOf(
                    flags.isName && strMatches(ch.yp4g.name),
                    flags.isDescription && strMatches(ch.yp4g.description),
                    flags.isComment && strMatches(ch.yp4g.comment),
                    flags.isGenre && strMatches(ch.yp4g.genre)
            )
        }
    }

    val isStarred get() = name.startsWith("[star]") && !flags.isNG

    fun copyFlags(flags: (Flags) -> Flags): Favorite {
        return Favorite(name, pattern, flags(this.flags), isEnabled)
    }

    companion object {
        fun createStarred(ch: Yp4gChannel): Favorite {
            return Favorite("[star]${ch.yp4g.name}", ch.yp4g.name, Flags(
                    isName = true, isExactMatch = true
            ))
        }
    }

    class Converter {
        @TypeConverter
        fun stringToFlags(s: String) : Flags {
            return try {
                FLAGS_ADAPTER.fromJson(s)
            } catch (e: IOException){
                Timber.e(e, "fromJson($e)")
                null
            } ?: Flags()
        }

        @TypeConverter
        fun flagsToString(flags: Flags) : String {
            return FLAGS_ADAPTER.toJson(flags)
        }

        companion object {
            private val FLAGS_ADAPTER = SquareUtils.MOSHI.adapter(Flags::class.java)
        }
    }
}


/**
 * YP4G/index.txtのChannel情報です。
 * @version 50000
 */
@Entity(tableName = "YpIndex",
        primaryKeys = ["name", "id"],
        indices = [Index("isLatest")])
class YpIndex : Yp4gChannel() {

    /**直近の読み込みか*/
    var isLatest: Boolean = false

    /**読み込んだ時刻。*/
    lateinit var lastLoadedTime: Date

    /**読み込み回数*/
    var numLoaded: Int = 0

    companion object {
        val COLUMNS = Yp4gColumn.values().map { "$it" } +
                listOf("isLatest", "lastLoadedTime", "numLoaded")
    }
}


/**
 * 再生の履歴
 * @version 50000
 * */
@Entity(tableName = "YpHistory",
        primaryKeys = ["name", "id"])
class YpHistory : Yp4gChannel() {
    /**最終再生日*/
    var lastPlay = Date()

    override val isEnabled get() = super.isEnabled && isPlayAvailable

    /**現在もYPに掲載されていて再生可能か*/
    @Ignore
    var isPlayAvailable = false

    companion object {
        fun from(copy: Yp4gChannel) = YpHistory().apply {
            yp4g = copy.yp4g
        }
    }
}


private const val TAG = "AppManageable"
