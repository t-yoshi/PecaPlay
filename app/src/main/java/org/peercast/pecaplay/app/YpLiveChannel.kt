package org.peercast.pecaplay.app

import androidx.room.Entity
import androidx.room.Index
import org.peercast.pecaplay.yp4g.Yp4gColumn
import org.peercast.pecaplay.yp4g.YpChannel
import java.util.*

/**
 * 現在配信中のChannel情報です。
 * @version 50100
 */
@Entity(tableName = "YpLiveChannel",
    primaryKeys = ["name", "id"],
    indices = [Index("isLatest")])
class YpLiveChannel : YpChannel() {
    /**直近の読み込みか*/
    var isLatest: Boolean = false

    /**読み込んだ時刻。*/
    lateinit var lastLoadedTime: Date

    /**読み込み回数*/
    var numLoaded: Int = 0

    companion object {
        val COLUMNS = Yp4gColumn.values().map { "$it" } +
                listOf("isLatest", "lastLoadedTime", "numLoaded")
    }
}