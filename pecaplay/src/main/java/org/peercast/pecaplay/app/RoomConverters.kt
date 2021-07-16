package org.peercast.pecaplay.app

import android.net.Uri
import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.*

class RoomConverters {
    @TypeConverter
    fun dateFromString(s: String?): Date? = newDateFormat().parse(s)

    @TypeConverter
    fun dateToString(d: Date): String = newDateFormat().format(d)

    @TypeConverter
    fun stringToUri(s: String): Uri = Uri.parse(s)

    @TypeConverter
    fun uriToString(u: Uri) = u.toString()

    companion object {
        private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")
        private const val DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss"

        private fun newDateFormat() =
            SimpleDateFormat(DATETIME_FORMAT, Locale.US).apply {
                timeZone = GMT_TIMEZONE
            }

    }
}