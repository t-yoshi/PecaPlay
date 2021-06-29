package org.peercast.pecaplay.worker

import kotlinx.coroutines.flow.MutableStateFlow


class LoadingEventFlow : MutableStateFlow<LoadingEvent?> by MutableStateFlow(null)