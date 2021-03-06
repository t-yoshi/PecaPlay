package org.peercast.pecaplay.app

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import org.peercast.pecaplay.util.SquareUtils
import org.peercast.pecaplay.yp4g.YpChannel
import timber.log.Timber
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

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
    override val isEnabled: Boolean = true,
) : ManageableEntity(), Parcelable {

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
        val isCaseSensitive: Boolean = false,
    ) : Parcelable

    fun matches(ch: YpChannel): Boolean {
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

    val isStar get() = name.startsWith("[star]") && !flags.isNG

    fun copyFlags(flags: (Flags) -> Flags): Favorite {
        return Favorite(name, pattern, flags(this.flags), isEnabled)
    }

    companion object {
        fun Star(ch: YpChannel): Favorite {
            return Favorite("[star]${ch.yp4g.name}", ch.yp4g.name, Flags(
                isName = true, isExactMatch = true
            ))
        }
    }

    class Converter {
        @TypeConverter
        fun stringToFlags(s: String): Flags {
            return try {
                FLAGS_ADAPTER.fromJson(s)
            } catch (e: IOException) {
                Timber.e(e, "fromJson($e)")
                null
            } ?: Flags()
        }

        @TypeConverter
        fun flagsToString(flags: Flags): String {
            return FLAGS_ADAPTER.toJson(flags)
        }

        companion object {
            private val FLAGS_ADAPTER = SquareUtils.MOSHI.adapter(Flags::class.java)
        }
    }
}