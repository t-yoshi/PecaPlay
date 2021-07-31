package org.peercast.pecaviewer.chat.adapter

import android.text.Spannable
import android.text.SpannableStringBuilder
import androidx.core.text.getSpans
import kotlinx.coroutines.flow.MutableStateFlow
import org.peercast.pecaviewer.chat.net.BbsMessage
import org.peercast.pecaviewer.chat.net.IMessage
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailSpan
import org.peercast.pecaviewer.chat.thumbnail.ThumbnailUrl
import org.peercast.pecaviewer.util.DateUtils

class MessageViewModel {
    /**レス番号*/
    val number = MutableStateFlow<CharSequence>("")

    /**名前*/
    val name = MutableStateFlow<CharSequence>("")

    /**日付*/
    val date = MutableStateFlow<CharSequence>("")

    val id = MutableStateFlow<CharSequence>("")

    /**本文*/
    val body = MutableStateFlow<CharSequence>("")

    val elapsedTime = MutableStateFlow<CharSequence>("")

    val thumbnails = MutableStateFlow<List<ThumbnailUrl>>(emptyList())

    fun setMessage(m: IMessage, isShowElapsedTime: Boolean = true) {
        number.value = "${m.number}"
        name.value = m.name
        date.value = m.date
        id.value = m.id

        thumbnails.value = when (val b = m.body) {
            is Spannable -> {
                b.getSpans<ThumbnailSpan>().take(16).map { it.url }
            }
            else -> emptyList()
        }

        if (isShowElapsedTime && m is BbsMessage && m.timeInMillis > 0) {
            val et = DateUtils.formatElapsedTime(System.currentTimeMillis() - m.timeInMillis)
            elapsedTime.value = et
            // elapsedTimeのぶん、末尾を空けておく
            val sbBody = SpannableStringBuilder(m.body.trimEnd())
            sbBody.append(NBSP.repeat(et.width + 2))
            body.value = sbBody
        } else {
            elapsedTime.value = ""
            body.value = m.body
        }
    }

    private val CharSequence.width: Int
        get() {
            return length + RE_LETTER.replace(this, "").length
        }

    companion object {
        private const val NBSP = "\u00a0"
        private val RE_LETTER = """[\da-zA-Z ]""".toRegex()
    }

}
