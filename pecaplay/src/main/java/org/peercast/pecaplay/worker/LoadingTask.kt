package org.peercast.pecaplay.worker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.peercast.pecaplay.yp4g.Yp4gColumn
import org.peercast.pecaplay.yp4g.net.Yp4gChannelBinder
import org.peercast.pecaplay.yp4g.net.createYp4gService
import timber.log.Timber
import java.io.IOException

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
        val binders = withContext(Dispatchers.IO) {
            yellowPages.map { yp ->
                val svc = createYp4gService(worker.square.okHttpClient, yp)
                async {
                    try {
                        svc.getIndex("localhost:$port")
                            .onEach { it.setYellowPage(yp) }
                    } catch (e: IOException) {
                        worker.eventFlow.value =
                            LoadingEvent.OnException(worker.id, yp, e)
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        worker.database.runInTransaction {
            worker.database.compileStatement("UPDATE YpLiveChannel SET isLatest=0").use {
                it.executeUpdateDelete()
            }
            if (binders.isNotEmpty()) {
                storeToYpLiveChannelTable(binders)
            }
        }

        return binders.isNotEmpty()
    }

    private fun storeToYpLiveChannelTable(binders: List<Yp4gChannelBinder>) {
        val sql = """
            REPLACE INTO YpLiveChannel (
              name, id, ip, url, genre,
              description, listeners, relays, bitrate, type,
              trackArtist, trackAlbum, trackTitle, trackContact, nameUrl,
              age, status, comment, direct, ypName,
              ypUrl, isLatest, lastLoadedTime, numLoaded  --#24
            ) VALUES(
              ?,?,?,?,?,?,?,?,?,?,
              ?,?,?,?,?,?,?,?,?,?,
              ?, --> ?*21 
              1, --> isLatest
              CURRENT_TIMESTAMP, --> lastLoadedTime
              IFNULL((SELECT numLoaded+1 FROM YpLiveChannel WHERE name=? AND id=?), 1) -->numLoaded
            )""".trimIndent()

        worker.database.compileStatement(sql).use { statement ->
            binders.forEach { b ->
                Timber.d("->%s", b)
                b.bindToStatement(
                    statement,
                    *Yp4gColumn.values(), Yp4gColumn.Name, Yp4gColumn.Id
                )
                val r = statement.executeInsert()
                Timber.d("## $r")
            }
        }

        //database.compileStatement("SELECT COUNT(*) FROM YpLiveChannel").use {
        //    Timber.d("num=%d", it.simpleQueryForLong())
        //}
    }
}