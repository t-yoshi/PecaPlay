package org.peercast.pecaplay

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.crashlytics.android.Crashlytics
import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.peercast.core.lib.PeerCastController
import org.peercast.core.lib.PeerCastRpcClient
import org.peercast.core.lib.notify.NotifyChannelType
import org.peercast.core.lib.notify.NotifyMessageType
import org.peercast.core.lib.rpc.ChannelInfo
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.list.listItemModule
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.prefs.DefaultAppPreferences
import org.peercast.pecaplay.prefs.PecaPlayViewerSetting
import timber.log.Timber
import java.io.IOException
import java.util.*


val appModule = module {
    single { AppRoomDatabase.createInstance(get(), "pecaplay-5") }
    single<AppPreferences> { DefaultAppPreferences(get()) }
    single { PeerCastServiceEventLiveData(get(), get()) }
    single { LoadingWorkerLiveData() }
    viewModel { PecaPlayViewModel(get(), get(), get(), get()) }
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
class PeerCastServiceEventLiveData(a: Application, private val appPrefs: AppPreferences) :
    MutableLiveData<PeerCastServiceBindEvent>(), PeerCastController.EventListener {

    private val controller = PeerCastController.from(a)

    init {
        controller.eventListener = this
    }

    fun bind() {
        if (!controller.isInstalled ||
            appPrefs.peerCastUrl.host !in listOf<String?>("localhost", "127.0.0.1")
        ) {
            //非インストール or 外部(=Lan?)動作のPeerCast
            postValue(PeerCastServiceBindEvent.OnBind(0))
            return
        }
        if (controller.isConnected && value is PeerCastServiceBindEvent.OnBind)
            return
        controller.bindService()
    }

    fun unbind() {
        controller.unbindService()
        value = PeerCastServiceBindEvent.OnUnbind
    }

    override fun onConnectService(controller: PeerCastController) {
        val client = PeerCastRpcClient(controller)

        GlobalScope.launch {
            val ev = try {
                val port = client.getStatus().globalRelayEndPoint?.port ?: 7144
                appPrefs.peerCastUrl = Uri.parse("http://localhost:$port/")
                PeerCastServiceBindEvent.OnBind(port)
            } catch (e: IOException) {
                Timber.e(e)
                PeerCastServiceBindEvent.OnBind(appPrefs.peerCastUrl.port)
            }
            postValue(ev)
        }
    }

    override fun onNotifyChannel(
        type: NotifyChannelType,
        channelId: String,
        channelInfo: ChannelInfo
    ) {
    }

    override fun onNotifyMessage(types: EnumSet<NotifyMessageType>, message: String) {
    }

    override fun onDisconnectService() {
        value = PeerCastServiceBindEvent.OnUnbind
    }
}

@Suppress("unused")
class PecaPlayApplication : Application() {
    private val appPrefs: AppPreferences by inject()

    override fun onCreate() {
        //不完全なapkをサイドローディングインストールしたユーザーに警告する
        if (MissingSplitsManagerFactory.create(this).disableAppIfMissingRequiredSplits()) {
            return
        }

        super.onCreate()

        Timber.plant(ReleaseTree())

        startKoin {
            //androidLogger(Level.DEBUG)
            androidContext(this@PecaPlayApplication)
            modules(appModule, listItemModule)
        }

        PecaPlayViewerSetting.initComponentSetting()
        AppTheme.initNightMode(this, appPrefs.isNightMode)
    }

}

private class ReleaseTree : Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.INFO || BuildConfig.DEBUG
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        if (t != null)
            Crashlytics.logException(t)
    }
}