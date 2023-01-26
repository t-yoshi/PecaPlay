package org.peercast.pecaviewer.chat.thumbnail.net

class ImageLoadingEvent(
    val url: String,
    val bytesRead: Long,
    val contentLength: Long,
    val done: Boolean,
)