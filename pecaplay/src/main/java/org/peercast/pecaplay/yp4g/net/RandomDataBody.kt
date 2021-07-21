package org.peercast.pecaplay.yp4g.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.*

/**速度測定用のランダムデータ*/
class RandomDataBody(
    /**bytes*/
    private val length: Int,
    /**upload速度制限 bytes/sec*/
    private val limit: Int,
    /**0 to 100*/
    private val onProgress: (Int) -> Unit,
) : RequestBody() {

    private val randomData = ByteArray(10 * 1024)

    init {
        Random().nextBytes(randomData)
    }

    override fun contentType() = "application/octet-stream".toMediaType()

    override fun writeTo(sink: BufferedSink) {
        var sent = 2 //送信済みbytes (\r\n)

        val tStart = System.currentTimeMillis()
        //速度違反か
        val isSpeeding = {
            val tNow = System.currentTimeMillis()
            sent > limit * (1 + (tNow - tStart) / 1000.0)
        }

        onProgress(0)
        while (sent < length) {
            val c = minOf(length - sent, randomData.size)
            sink.write(randomData, 0, c).flush()

            sent += c
            onProgress(100 * sent / length)

            sink.flush()

            while (isSpeeding()) {
                Thread.sleep(10)
            }
        }
        // \r\n
        sink.writeByte(0x0d)
            .writeByte(0x0a)
    }

    override fun contentLength() = length.toLong()

    override fun toString() = "RandomDataBody length=$length limit=$limit"
}