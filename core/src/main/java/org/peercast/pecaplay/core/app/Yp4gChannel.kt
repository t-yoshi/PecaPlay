package org.peercast.pecaplay.core.app

import android.net.Uri
import android.os.Parcelable

/**
 * YP4G/index.txtのChannel情報です。
 */
interface Yp4gChannel : Parcelable {
    val name: String //#0
    val id: String
    val ip: String
    val url: Uri
    val genre: String
    val description: String// #5
    val listeners: Int
    val relays: Int
    val bitrate: Int
    val type: String
    val trackArtist: String// #10
    val trackAlbum: String
    val trackTitle: String
    val trackContact: String
    val nameUrl: Uri
    val age: String// #15
    val status: String
    val comment: String
    val direct: String
    // __END_OF_index_txt

    val ypName: String
    val ypUrl: Uri// #20
}


fun Yp4gChannel.playlist(peca: Uri): Uri =
    Uri.parse("http://${peca.host}:${peca.port}/pls/$id?tip=$ip")

fun Yp4gChannel.chatUrl(): Uri {
    return Uri.withAppendedPath(ypUrl, "chat.php?cn=$name")
}

fun Yp4gChannel.stream(peca: Uri): Uri {
    return when (val t = type.lowercase()) {
        "wmv" ->
            Uri.parse("mmsh://${peca.host}:${peca.port}/stream/$id.wmv?tip=$ip")
        "flv", "webm", "mkv" ->
            Uri.parse("http://${peca.host}:${peca.port}/stream/$id.$t?tip=$ip")
        else -> playlist(peca)
    }
}

fun Yp4gChannel.statisticsUrl(): Uri {
    return Uri.withAppendedPath(ypUrl, "getgmt.php?cn=$name")
}
