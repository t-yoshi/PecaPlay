package org.peercast.pecaplay.chanlist

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YpHistoryChannel
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.yp4g.YpChannel
import java.text.DateFormat


class ListItemViewModel(
    c: Context,
    val ch: YpChannel,
    val star: Favorite?,
    var isNg: Boolean,
    val isNotification: Boolean,
) {

    val name: CharSequence = ch.name
    val listener: CharSequence = when (ch) {
        is YpHistoryChannel -> ""
        else -> ch.formatListeners(c)
    }

    val description = SpannableStringBuilder().also {
        val playing = if (ch is YpHistoryChannel && !ch.isPlayable)
            "Played:   "
        else
            "Playing:  "
        it.append(playing, SPAN_ITALIC, 0)
        it.append("${ch.genre} ${ch.description}")
    }

    val comment = SpannableStringBuilder().also {
        it.append(ch.comment)
        //末尾にnbspを加えてlistener表示と重ならないようにする
        it.append(
            "\u00A0".repeat(listener.length),
            SPAN_MONOSPACE, 0
        )
    }

    val age: CharSequence = if (ch is YpHistoryChannel) {
        DATE_FORMAT.format(ch.lastPlay)
    } else {
        ch.age
    }

    val isEnabled = ch.isPlayable
    val isStarChecked = star != null
    val isStarEnabled = !ch.isEmptyId

    val isNewVisible = !ch.isEmptyId && ch is YpLiveChannel && ch.numLoaded <= 2
    val isNewChecked = ch is YpLiveChannel && ch.numLoaded == 1

    val isAgeVisible = !ch.isEmptyId

    val isNotifiedVisible = false


    companion object {
        private val SPAN_ITALIC = StyleSpan(Typeface.ITALIC)
        private val SPAN_MONOSPACE = TypefaceSpan("monospace")
        private val DATE_FORMAT = DateFormat.getDateInstance()

        @SuppressLint("StringFormatMatches")
        private fun YpChannel.formatListeners(c: Context): CharSequence {
            return c.getString(R.string.ch_listeners_fmt, listeners, relays, type)
        }
    }

    override fun toString() = "$name"
}
