package org.peercast.pecaviewer.chat.thumbnail

import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow

class ItemViewModel {
    val src = MutableStateFlow<Drawable?>(null)
    val background = MutableStateFlow<Drawable?>(null)
    val error = MutableStateFlow<CharSequence?>("loading..")
    val isTooLargeFileSize = MutableStateFlow(false)
    val isLinkUrl = MutableStateFlow(false)
    val isAnimation = MutableStateFlow(false)
}

