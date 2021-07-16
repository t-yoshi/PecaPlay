package org.peercast.pecaviewer

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerServiceEventLiveData
import org.peercast.pecaviewer.util.DefaultSquareHolder
import org.peercast.pecaviewer.util.ISquareHolder

val pecaviewerModule = module {
    single { ViewerPreference(get()) }
    viewModel { (pvm: PlayerViewModel, cvm: ChatViewModel) -> ViewerViewModel(get(), pvm, cvm) }
    viewModel { PlayerViewModel(get()) }
    viewModel { ChatViewModel(get()) }

    single<ISquareHolder> { DefaultSquareHolder(get()) }
    single { PlayerServiceEventLiveData() }
}

