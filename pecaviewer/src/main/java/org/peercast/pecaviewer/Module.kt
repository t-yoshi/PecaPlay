package org.peercast.pecaviewer

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.chat.thumbnail.net.ImageLoadingEventFlow
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service.PlayerServiceEventFlow

val pecaviewerModule = module {
    single { PecaViewerPreference(get()) }
    viewModel { (pvm: PlayerViewModel, cvm: ChatViewModel) -> PecaViewerViewModel(get(), pvm, cvm) }
    viewModel { PlayerViewModel(get()) }
    viewModel { ChatViewModel(get()) }

    single { ImageLoadingEventFlow() }
    single { PlayerServiceEventFlow() }
}

