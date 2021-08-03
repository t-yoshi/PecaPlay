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
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.prefs.DefaultAppPreferences
import org.peercast.pecaplay.worker.LoadingEventFlow
import org.peercast.pecaviewer.pecaviewerModule
import timber.log.Timber
import java.util.*

private val pecaplayModule = module {
    single { AppRoomDatabase.createInstance(get(), "pecaplay-5") }
    single<AppPreferences> { DefaultAppPreferences(get()) }
    single { LoadingEventFlow() }
    viewModel { AppViewModel(get(), get(), get(), get(), get()) }
}


@Suppress("unused")
class PecaPlayApplication : Application() {
    private val appPrefs: AppPreferences by inject()

    private lateinit var kApp: KoinApplication

    override fun onCreate() {
        super.onCreate()

        Timber.plant(ReleaseTree())

        kApp = startKoin {
            //androidLogger(Level.DEBUG)
            androidContext(this@PecaPlayApplication)
            modules(coreModule, pecaplayModule, pecaviewerModule)
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

