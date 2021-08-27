package org.peercast.pecaviewer.chat.thumbnail

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
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
    var onThumbnailClickedListener: OnThumbnailClickedListener? = null

    interface OnThumbnailClickedListener {
        fun onThumbnailClicked(
            thumbnail: ImageView,
            urls: List<ThumbnailUrl>,
            position: Int,
        )
    }
}




