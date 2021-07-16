package org.peercast.pecaviewer.chat.thumbnail

import android.text.Spannable
import android.text.TextPaint
import android.text.style.CharacterStyle
import androidx.core.text.set

class ThumbnailSpan(val url: ThumbnailUrl) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) {
    }

    companion object {
        fun Spannable.applyThumbnailSpan(): Spannable {
            // '/'を含まないならURLではない
            if (this.contains('/')) {
                ThumbnailUrl.findAll(this).forEach {
                    this[it.key.first, it.key.last] = ThumbnailSpan(it.value)
                }
            }
            return this
        }

    }

}

