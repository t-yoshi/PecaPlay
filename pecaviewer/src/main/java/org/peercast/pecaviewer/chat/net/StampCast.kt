package org.peercast.pecaviewer.chat.net


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.peercast.pecaplay.core.io.Square
import org.peercast.pecaplay.core.io.await
import java.io.IOException

@Serializable
data class StampCastStamps(
    val stamps: List<StampCastStamp>,
)

@Serializable
data class StampCastTag(
    val id: Int,
    val text: String,
)

@Serializable
data class StampCastStamp(
    val id: Int,
    val name: String,

    @SerialName("room_id")
    val roomId: Int,

    //@SerialName(name = "is_animation")
    //val isAnimation: Boolean,

    val tags: List<StampCastTag>,

    val thumbnail: String,

    @SerialName("user_id")
    val userId: String?,
) {

    fun toMessage(threadInfo: IThreadInfo, number: Int): IMessage {
        return BbsMessage(threadInfo, number, name, "", "", "<img src=$thumbnail>", "$id")
    }
}

private data class StampCastBoardInfo(
    override val title: String,
    override val url: String,
) : IBoardInfo

private data class StampCastThreadInfo(
    override val board: IBoardInfo, val page: Int,
) : IThreadInfo {
    override val url = board.url
    override val title = "$page"
    override val creationDate = ""
    override val numMessages = 30
    override val isPostable = false
}

private class StampCastConnection(val id: Int) : IBoardConnection {

    override val info = StampCastBoardInfo(
        "StampCast (???)",
        "https://stamp.archsted.com/$id"
    )

    override suspend fun loadThreads(): List<IThreadInfo> {
        return listOf(
            StampCastThreadInfo(info, 1)
        )
    }

    override suspend fun openThreadConnection(threadInfo: IThreadInfo): IBoardThreadConnection {
        if (threadInfo !is StampCastThreadInfo || threadInfo.board != info)
            throw IllegalArgumentException("wrong threadInfo: $threadInfo")
        return StampCastPageConnection(this, threadInfo)
    }

}

private class StampCastPageConnection(
    private val base: StampCastConnection,
    override val info: StampCastThreadInfo,
) : IBoardThreadConnection, IBoardConnection by base, KoinComponent {

    private val square by inject<Square>()

    override suspend fun loadMessages(): List<IMessage> {
        val req = Request.Builder()
            .url("https://stamp.archsted.com/api/v1/rooms/${base.id}/stamps/guest?page=${info.page}&sort=all&tag=")
            .header("Cache-Control", "private, must-revalidate, max-stale=5")
            .build()

        return square.okHttpClient.newCall(req).await { res ->
            val s = res.body?.string() ?: throw IOException("body is null")
            try {
                format.decodeFromString<StampCastStamps>(s)
            } catch (e: SerializationException) {
                throw IOException(e)
            }.stamps.mapIndexed { i, m ->
                m.toMessage(info, i + 1)
            }
        }
    }

    companion object {
        private val format = Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}

object StampCastConnectionFactory : ConnectionFactory() {
    private val RE_URL = """^https?://stamp\.archsted\.com/(\d+)""".toRegex()

    override suspend fun invoke(url: String): IBoardConnection? {
        val m = RE_URL.matchEntire(url) ?: return null
        val base = StampCastConnection(m.groupValues[1].toInt())
        return base.openThreadConnection(base.loadThreads().first())
    }
}