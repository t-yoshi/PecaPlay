package org.peercast.pecaplay.app

import android.net.Uri
import androidx.room.Entity
import kotlinx.parcelize.Parcelize
import org.peercast.pecaplay.yp4g.YpChannel
import java.util.*

/**
 * 再生した履歴
 * @version 50100
 * */
@Parcelize
@Entity(
    tableName = "YpHistoryChannel",
    //配信者のidはランダム化されるようになったが、DBをいじりたくないので
    // SELECT ... GROUP BY name で対応する
    primaryKeys = ["name", "id"]
)
data class YpHistoryChannel constructor(
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

    /**最終再生日*/
    val lastPlay: Date,

    ) : YpChannel() {

    constructor(ch: YpChannel, lastPlay: Date = Date()) : this(
        name = ch.name,
        id = ch.id,
        ip = ch.ip,
        url = ch.url,
        genre = ch.genre,
        description = ch.description,
        listeners = ch.listeners,
        relays = ch.relays,
        bitrate = ch.bitrate,
        type = ch.type,
        trackArtist = ch.trackArtist,
        trackAlbum = ch.trackAlbum,
        trackTitle = ch.trackTitle,
        trackContact = ch.trackContact,
        nameUrl = ch.nameUrl,
        age = ch.age,
        status = ch.status,
        comment = ch.comment,
        direct = ch.direct,
        ypName = ch.ypName,
        ypUrl = ch.ypUrl,
        lastPlay = lastPlay
    )

    override val isPlayable get() = false
}
