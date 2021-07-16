package org.peercast.pecaplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.prefs.PecaPlayPreferences
import org.peercast.pecaplay.worker.LoadingWorkerManager

class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val appPrefs by inject<PecaPlayPreferences>()

    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (appPrefs.isNotificationEnabled) {
                    LoadingWorkerManager(c).enqueuePeriodic()
                }
            }
        }
    }
}