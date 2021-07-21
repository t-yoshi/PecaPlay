package org.peercast.pecaplay

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.list.listItemModule
import org.peercast.pecaplay.prefs.PecaPlayPreferences
import org.peercast.pecaplay.prefs.DefaultPecaPlayPreferences
import org.peercast.pecaviewer.pecaviewerModule
import org.peercast.pecaplay.worker.LoadingEventFlow
import timber.log.Timber
import java.util.*


private val pecaplayModule = module {
    single { AppRoomDatabase.createInstance(get(), "pecaplay-5") }
    single<PecaPlayPreferences> { DefaultPecaPlayPreferences(get()) }
    single { LoadingEventFlow() }
    viewModel { PecaPlayViewModel(get(), get(), get()) }
}

@Suppress("unused")
class PecaPlayApplication : Application() {
    private val pecaPlayPrefs: PecaPlayPreferences by inject()

    private lateinit var kApp: KoinApplication

    override fun onCreate() {
        super.onCreate()

        Timber.plant(ReleaseTree())

        kApp = startKoin {
            //androidLogger(Level.DEBUG)
            androidContext(this@PecaPlayApplication)
            modules(coreModule, pecaplayModule, pecaviewerModule, listItemModule)
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
}

