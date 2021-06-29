package org.peercast.pecaplay.app

import androidx.room.Entity
import androidx.room.Ignore
import org.peercast.pecaplay.yp4g.YpChannel
import java.util.*

/**
 * 再生した履歴
 * @version 50100
 * */
@Entity(tableName = "YpHistoryChannel",
    primaryKeys = ["name", "id"])
class YpHistoryChannel() : YpChannel() {
    /**最終再生日*/
    lateinit var lastPlay: Date

    /**現在もYPに掲載されていて再生可能か*/
    @Ignore
    override var isEnabled = false
        set(value) {
            field = value && super.isEnabled
        }

    constructor(copy: YpChannel, lastPlay: Date = Date()) : this() {
        yp4g = copy.yp4g
        this.lastPlay = lastPlay
    }
}