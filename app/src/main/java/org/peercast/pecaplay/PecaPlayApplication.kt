package org.peercast.pecaplay

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*
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
import kotlin.coroutines.CoroutineContext


val appModule = module {
    single { AppRoomDatabase.createInstance(get(), "pecaplay-5") }
    single<AppPreferences> { DefaultAppPreferences(get()) }
    single { LoadingWorkerLiveData() }
    viewModel { PecaPlayViewModel(get(), get(), get()) }
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
            FirebaseCrashlytics.getInstance().recordException(t)
    }
}