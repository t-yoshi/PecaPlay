package org.peercast.pecaviewer

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerServiceEventFlow

val pecaviewerModule = module {
    single { ViewerPreference(get()) }
    viewModel { (pvm: PlayerViewModel, cvm: ChatViewModel) -> ViewerViewModel(get(), pvm, cvm) }
    viewModel { PlayerViewModel(get()) }
    viewModel { ChatViewModel(get()) }

    single { PlayerServiceEventFlow() }
}

