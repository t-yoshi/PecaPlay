package org.peercast.pecaviewer.util

import android.app.Application
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaviewer.R
import java.util.*
import kotlin.math.abs

internal object DateUtils : KoinComponent {
    private val a by inject<Application>()

    private val RE_DATETIME_1 = """(20\d\d)/([01]?\d)/(\d\d).*(\d\d):(\d\d):(\d\d)""".toRegex()
    private val JP_CALENDAR = Calendar.getInstance(Locale.JAPAN)

    fun parseData(s: CharSequence, timeZone: String = "Asia/Tokyo"): Long {
        RE_DATETIME_1.find(s)?.let { ma ->
            val a = ma.groupValues.drop(1).map { it.toInt() }
            synchronized(JP_CALENDAR) {
                JP_CALENDAR.set(a[0], a[1] - 1, a[2], a[3], a[4], a[5])
                JP_CALENDAR.timeZone = TimeZone.getTimeZone(timeZone)
                try {
                    return JP_CALENDAR.timeInMillis
                } catch (e: IllegalArgumentException) {
                }
            }
        }
        return 0L
    }

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val YEAR = 365 * DAY

    fun formatElapsedTime(t: Long): String {
        val t = abs(t)

        return when {
            t > YEAR -> a.getString(R.string.et_years_fmt, t / YEAR)
            t > DAY -> a.getString(R.string.et_days_fmt, t / DAY)
            t > HOUR -> a.getString(R.string.et_hours_fmt, t / HOUR)
            t > MINUTE -> a.getString(R.string.et_minutes_fmt, t / MINUTE)
            else -> a.getString(R.string.et_seconds_fmt, t / 1000)
        }
    }

}