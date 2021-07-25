package org.peercast.pecaplay.navigation

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.prefs.PecaPlayPreferences

class NavigationRepository(private val model: NavigationModel) : KoinComponent {

    private val database by inject<AppRoomDatabase>()

    private var j: Job? = null

    fun collectIn(lifecycleCoroutineScope: LifecycleCoroutineScope) {
        j?.cancel()
        j = lifecycleCoroutineScope.launchWhenCreated {
            combine(
                database.yellowPageDao.query(),
                database.favoriteDao.query(),
                database.ypChannelDao.query(),
                model::updateItems
            ).collect()
        }
    }


}