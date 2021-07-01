package org.peercast.pecaplay.worker

import android.content.Context
import android.net.Uri
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.core.lib.PeerCastRpcClient
import org.peercast.core.lib.app.BaseClientWorker
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.prefs.AppPreferences
import org.peercast.pecaplay.worker.LoadingEvent
import org.peercast.pecaplay.worker.LoadingEventFlow
import org.peercast.pecaplay.worker.LoadingTask
import org.peercast.pecaplay.worker.NotificationTask


class LoadingWorker(c: Context, workerParams: WorkerParameters) :
    BaseClientWorker(c, workerParams), KoinComponent {

    val database by inject<AppRoomDatabase>()
    val appPrefs by inject<AppPreferences>()
    val eventFlow by inject<LoadingEventFlow>()

    abstract class Task {
        /**trueなら次のタスクを実行する*/
        abstract suspend operator fun invoke(): Boolean
    }

    override fun getPeerCastUrl(): Uri {
        return appPrefs.peerCastUrl
    }

    override suspend fun doWorkOnServiceConnected(client: PeerCastRpcClient): Result {
        eventFlow.value = LoadingEvent.OnStart(id)
        val tasks = listOf(
            LoadingTask(this),
            NotificationTask(this)
        )
        try {
            for (t in tasks) {
                if (!t())
                    return Result.failure()
            }
        } catch (t: Throwable) {
            //NOTE: 例外が起きても[androidx.work.impl.WorkerWrapper]内で
            //キャッチされるだけ。補足しにくいので注意。
            FirebaseCrashlytics.getInstance().recordException(t)
            throw t
        } finally {
            eventFlow.value = LoadingEvent.OnFinished(id)
        }
        return Result.success()
    }

}

