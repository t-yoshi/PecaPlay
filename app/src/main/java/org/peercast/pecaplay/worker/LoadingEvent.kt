package org.peercast.pecaplay.worker

import org.peercast.pecaplay.app.YellowPage
import java.io.IOException
import java.util.*

sealed class LoadingEvent(val id: UUID) {
    class OnStart(id: UUID) : LoadingEvent(id)
    class OnException(id: UUID, val yp: YellowPage, val e: IOException) : LoadingEvent(id)
    class OnFinished(id: UUID) : LoadingEvent(id)
}