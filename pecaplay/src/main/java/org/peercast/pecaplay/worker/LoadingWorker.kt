package org.peercast.pecaplay.worker

import android.content.Context
import android.net.Uri
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.core.lib.PeerCastRpcClient
import org.peercast.core.lib.app.BaseClientWorker
import org.peercast.pecaplay.prefs.PecaPlayPreferences
import timber.log.Timber


class LoadingWorker(c: Context, workerParams: WorkerParameters) :
    BaseClientWorker(c, workerParams), KoinComponent {

    private val appPrefs by inject<PecaPlayPreferences>()
    private val eventFlow by inject<LoadingEventFlow>()

    abstract class Task(protected val worker: ListenableWorker) {
        /**trueなら次のタスクを実行する*/
        abstract suspend fun invoke(): Boolean
    }

    override fun getPeerCastUrl(): Uri {
        return appPrefs.peerCastUrl
    }

    override suspend fun doWorkOnServiceConnected(client: PeerCastRpcClient): Result {
        eventFlow.value = LoadingEvent.OnStart(id)
        val tasks = listOf(
            ::DatabaseTruncateTask,
            ::LoadingTask,
            ::NotificationTask,
        )
        try {
            for (t in tasks) {
                if (!t(this).invoke())
                    return Result.failure()
            }
        } catch (t: Throwable) {
            //NOTE: 例外が起きても[androidx.work.impl.WorkerWrapper]内で
            //キャッチされるだけ。補足しにくいので注意。
            Timber.e(t, "An exception happened in LoadingWorker.")
            throw t
        } finally {
            eventFlow.value = LoadingEvent.OnFinished(id)
        }
        return Result.success()
    }

}

