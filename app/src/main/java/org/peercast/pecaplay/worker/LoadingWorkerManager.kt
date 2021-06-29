package org.peercast.pecaplay.worker

import android.content.Context
import androidx.work.*
import org.peercast.pecaplay.LoadingWorker
import java.util.concurrent.TimeUnit

class LoadingWorkerManager(c: Context) {
    private val workManager = WorkManager.getInstance(c)

    fun enqueuePeriodic() {
        //15分毎にYP読み込みを行う
        val req = PeriodicWorkRequest
            .Builder(LoadingWorker::class.java, 15, TimeUnit.MINUTES)
            .addTag(LOADING_WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5L, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueue(req)
    }

    fun enqueueOneshot() {
        //1回限り
        workManager.beginUniqueWork(
            WORK_NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequest.Builder(LoadingWorker::class.java)
                .addTag(LOADING_WORK_TAG)
                .build()
        ).enqueue()
    }

    fun cancel() {
        workManager.cancelAllWorkByTag(LOADING_WORK_TAG)
    }

    companion object {
        private const val WORK_NAME = "yp_loading_work"
        private const val LOADING_WORK_TAG = "yp_loading_work_tag"
    }
}