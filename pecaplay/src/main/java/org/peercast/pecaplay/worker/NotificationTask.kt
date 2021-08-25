package org.peercast.pecaplay.worker

import androidx.work.ListenableWorker
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.PecaPlayNotification
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.prefs.AppPreferences

class NotificationTask(worker: ListenableWorker) : LoadingWorker.Task(worker), KoinComponent {

    private val database by inject<AppRoomDatabase>()
    private val appPrefs by inject<AppPreferences>()
    private val c = worker.applicationContext

    override suspend fun invoke(): Boolean {
        if (!appPrefs.isNotificationEnabled)
            return true

        val channels = database.ypChannelDao.query().first()
        val favorites = database.favoriteDao.query().first()

        val favoNotify = favorites.filter { it.flags.isNotification }
        val favoNG = favorites.filter { it.flags.isNG }

        val newChannels = channels.filter { ch ->
            ch.numLoaded == 1 &&
                    favoNotify.any { it.matches(ch) } &&
                    !favoNG.any { it.matches(ch) }
        }

        if (newChannels.isNotEmpty()) {
            PecaPlayNotification(c)
                .notifyNewYpChannelsWereFound(newChannels)
        }

        return true
    }

}