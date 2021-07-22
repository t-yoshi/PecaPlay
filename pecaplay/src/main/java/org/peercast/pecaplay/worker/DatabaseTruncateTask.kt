package org.peercast.pecaplay.worker

import androidx.work.ListenableWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.app.AppRoomDatabase
import timber.log.Timber

class DatabaseTruncateTask(worker: ListenableWorker) : LoadingWorker.Task(worker), KoinComponent {

    private val database by inject<AppRoomDatabase>()

    override suspend fun invoke(): Boolean {
        database.runInTransaction {
            var r = database.compileStatement(
                """
DELETE FROM YpHistoryChannel WHERE rowid NOT IN (
  SELECT rowid FROM YpHistoryChannel ORDER BY LastPlay DESC LIMIT 100
)""".trimIndent()
            ).use {
                it.executeUpdateDelete()
            }

            r += database.compileStatement(
                """
DELETE FROM YpLiveChannel
  WHERE lastLoadedTime < DATETIME('now', '-12 hours', PRINTF('-%d hours', age))
                """.trimIndent()
            ).use {
                it.executeUpdateDelete()
            }
        }
        return true
    }
}