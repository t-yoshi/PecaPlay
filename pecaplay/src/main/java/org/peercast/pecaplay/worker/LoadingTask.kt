package org.peercast.pecaplay.worker

import kotlinx.coroutines.flow.first
import org.peercast.pecaplay.yp4g.Yp4gColumn
import org.peercast.pecaplay.yp4g.net.Yp4gChannelBinder
import org.peercast.pecaplay.yp4g.net.createYp4gService
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
        val lines = ArrayList<Yp4gChannelBinder>(256)

        yellowPages.forEach { yp ->
            try {
                val res = createYp4gService(
                    worker.square.okHttpClient, yp
                ).getIndex("localhost:$port")
                res.forEach {
                    it.setYellowPage(yp)
                }
                lines.addAll(res)
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

    private fun storeToYpLiveChannelTable(lines: List<Yp4gChannelBinder>) {
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
            lines.forEach { line ->
                Timber.d("->%s", line)
                line.bindToStatement(
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