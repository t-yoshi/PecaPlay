package org.peercast.pecaviewer.chat.thumbnail

import android.content.Context
import android.util.AttributeSet
import com.google.android.flexbox.FlexboxLayout

class ThumbnailView : FlexboxLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    val adapter = ViewAdapter(this)
    var eventListener: OnItemEventListener? = null

    interface OnItemEventListener {
        fun onLaunchImageViewer(u: ThumbnailUrl)
    }
}




