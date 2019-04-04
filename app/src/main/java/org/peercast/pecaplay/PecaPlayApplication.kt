package org.peercast.pecaplay

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.peercast.core.PeerCastController
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.list.listItemModule
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.prefs.DefaultAppPreferences
import timber.log.Timber


val appModule = module {
    single { AppRoomDatabase.createInstance(get(), "pecaplay-5") }
    single<AppPreferences> { DefaultAppPreferences(get()) }
    single { PeerCastServiceEventLiveData() }
    single { LoadingWorkerLiveData() }
    viewModel { PecaPlayViewModel(get(), get(), get()) }
}


/**ローカルでのPeerCastServiceにbind/unbindした */
sealed class PeerCastServiceBindEvent {
    //外部での動作は0
    data class OnBind(val localServicePort: Int) : PeerCastServiceBindEvent()

    object OnUnbind : PeerCastServiceBindEvent()
}

/**
 * PeerCastServiceへのbind/unbindイベント。
 * YPへの読み込みはPeerCastアプリが起動してから行う。
 * */
class PeerCastServiceEventLiveData : MediatorLiveData<PeerCastServiceBindEvent>()


class PecaPlayApplication : Application(), PeerCastController.EventListener {
    private val appPrefs: AppPreferences by inject()
    private val serviceEventLiveData: PeerCastServiceEventLiveData by inject()

    override fun onCreate() {
        super.onCreate()

        Timber.plant(ReleaseTree())

        startKoin {
            //androidLogger(Level.DEBUG)
            androidContext(this@PecaPlayApplication)
            modules(appModule, listItemModule)
        }

        AppTheme.initNightMode(this, appPrefs.isNightMode)

        val controller = PeerCastController.from(this)
        if (controller.isInstalled && appPrefs.peerCastUrl.host in listOf<String?>("localhost", "127.0.0.1")) {
            controller.addEventListener(this)
            controller.bindService()
        } else {
            serviceEventLiveData.value = PeerCastServiceBindEvent.OnBind(0)
        }
    }

    override fun onConnectService(controller: PeerCastController) {
        GlobalScope.launch(Dispatchers.Main) {
            val port = controller.getsProperties().port
            appPrefs.peerCastUrl = Uri.parse("http://localhost:$port/")
            serviceEventLiveData.value = PeerCastServiceBindEvent.OnBind(port)
        }
    }

    override fun onDisconnectService(controller: PeerCastController) {
        serviceEventLiveData.value = PeerCastServiceBindEvent.OnUnbind
    }

}

private class ReleaseTree : Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.INFO || BuildConfig.DEBUG
    }
}