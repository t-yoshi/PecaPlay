package org.peercast.pecaviewer.chat.thumbnail.net

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class ImageLoadingEventFlow :
    MutableSharedFlow<ImageLoadingEvent> by MutableSharedFlow(1, 0, BufferOverflow.DROP_OLDEST)
