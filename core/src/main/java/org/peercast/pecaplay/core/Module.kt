package org.peercast.pecaplay

import org.koin.dsl.module
import org.peercast.pecaplay.core.io.DefaultSquare
import org.peercast.pecaplay.core.io.Square

val coreModule = module {
    single<Square> { DefaultSquare(get()) }
}