package org.peercast.pecaplay.app

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import kotlinx.parcelize.Parcelize
import org.peercast.pecaplay.yp4g.Yp4gColumn
import org.peercast.pecaplay.yp4g.YpChannel
import java.util.*

/**
 * 現在配信中のChannel情報です。
 * @version 50100
 * @see LoadingTask#storeToYpLiveChannelTable
 */
@Parcelize
@Entity(tableName = "YpLiveChannel",
    primaryKeys = ["name", "id"],
    indices = [Index("isLatest")])
data class YpLiveChannel(
    override val name: String,
    override val id: String,
    override val ip: String,
    override val url: Uri,
    override val genre: String,
    override val description: String,
    override val listeners: Int,
    override val relays: Int,
    override val bitrate: Int,
    override val type: String,
    override val trackArtist: String,
    override val trackAlbum: String,
    override val trackTitle: String,
    override val trackContact: String,
    override val nameUrl: Uri,
    override val age: String,
    override val status: String,
    override val comment: String,
    override val direct: String,
    override val ypName: String,
    override val ypUrl: Uri,
    // __end_of_IYp4gChannel

    /**直近のYPに掲載されているか*/
    val isLatest: Boolean = false,

    /**直近のYPを読み込んだ時刻*/
    val lastLoadedTime: Date,

    /**過去、n回前の読み込みからYPに掲載されている*/
    val numLoaded: Int = 0,
) : YpChannel()
