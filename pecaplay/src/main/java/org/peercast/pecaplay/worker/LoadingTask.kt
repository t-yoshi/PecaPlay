package org.peercast.pecaplay.worker

import kotlinx.coroutines.flow.first
import org.peercast.pecaplay.app.YpLiveChannel
import org.peercast.pecaplay.yp4g.*
import timber.log.Timber
import java.io.IOException
import java.util.*

class LoadingTask(
    private val worker: LoadingWorker,
) : LoadingWorker.Task() {

    override suspend fun invoke(): Boolean {
        if (worker.database.ypChannelDao.getLastLoadedSince() < 15) {
            return false
        }

        val yellowPages = worker.database.yellowPageDao.query().first()

        Timber.d("start loading: %s", yellowPages)

        val port = worker.appPrefs.peerCastUrl.port
        val lines = ArrayList<Yp4gRawField>(256)

        yellowPages.forEach { yp ->
            try {
                val res = createYp4gService(yp).getIndex("localhost:$port")
                val url = res.raw().request.url.toString()
                res.body()?.mapNotNull {
                    try {
                        it.create(yp, url)
                    } catch (e: Yp4gFormatException) {
                        Timber.w("YpParseError: %s", e.message)
                        null
                    }
                }?.let(lines::addAll)
            } catch (e: IOException) {
                worker.eventFlow.value =
                    LoadingEvent.OnException(worker.id, yp, e)
            }
        }

        worker.database.runInTransaction {
            worker.database.compileStatement("UPDATE YpLiveChannel SET isLatest=0").use {
                it.executeUpdateDelete()
            }
            if (lines.isNotEmpty()) {
                storeToYpLiveChannelTable(lines)
            }
        }

        return lines.isNotEmpty()
    }

    private fun storeToYpLiveChannelTable(lines: List<Yp4gRawField>) {
        val sql = "REPLACE INTO YpLiveChannel (" +
                YpLiveChannel.COLUMNS.joinToString(",") +
                ") VALUES(" +
                Yp4gColumn.values().joinToString(",", transform = { "?" }) +
                """,
                1, --> isLatest
                CURRENT_TIMESTAMP, --> lastLoadedTime
                IFNULL((SELECT numLoaded+1 FROM YpLiveChannel WHERE name=? AND id=?), 1) -->numLoaded
            )""".trimIndent()

        val columnNames = Yp4gColumn.values().toList() + Yp4gColumn.Name + Yp4gColumn.Id

        worker.database.compileStatement(sql).use { statement ->
            lines.forEach { line ->
                Timber.d("->%s", line)
                line.bindTo(statement, columnNames)

                val r = statement.executeInsert()
                Timber.d("## $r")
            }
        }

        //database.compileStatement("SELECT COUNT(*) FROM YpLiveChannel").use {
        //    Timber.d("num=%d", it.simpleQueryForLong())
        //}
    }
}