package org.peercast.pecaplay.navigation

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.app.AppRoomDatabase

class NavigationRepository(private val model: NavigationModel) : KoinComponent {

    private val database by inject<AppRoomDatabase>()

    suspend fun collect() {
        combine(
            database.yellowPageDao.query(),
            database.favoriteDao.query(),
            database.ypChannelDao.query(),
            model::updateItems
        ).collect()
    }

}