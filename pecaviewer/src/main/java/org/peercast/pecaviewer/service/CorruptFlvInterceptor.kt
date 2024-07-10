package org.peercast.pecaviewer.service

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.IOException
import okio.Source
import okio.buffer
import timber.log.Timber

/**
 * 「もりも」などで音声しか再生されない
 *   -> FLVのヘッダーフラグが0x05であるべきなのに0x04だから
 *
 * https://en.wikipedia.org/wiki/Flash_Video より
 *
 * Header
 * FLV files start with a standard header which is shown below:[19]
 * Field	Data Type	Default	Details
 * Signature	byte[3]	"FLV"	Always "FLV"
 * Version	uint8	1	Only 0x01 is valid
 * Flags	uint8 bitmask	0x05	Bitmask: 0x04 is audio, 0x01 is video (so 0x05 is audio+video)
 * Header Size	uint32_be	9	Used to skip a newer expanded header
 *
 * */
object CorruptFlvInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val res = chain.proceed(req)
        val isFlv = res.body?.contentType()?.let {
            it.type == "video" && "flv" in it.subtype
        } ?: false
        if (isFlv) {
            return res.newBuilder()
                .body(forceFlvVideoFlag(res.body!!))
                .build()
        }
        return res
    }

    private fun forceFlvVideoFlag(body: ResponseBody): ResponseBody {
        return object : ResponseBody() {
            override fun contentType() = body.contentType()

            override fun contentLength() = body.contentLength()

            override fun source(): BufferedSource {
                return FlvSource(body.source()).buffer()
            }
        }
    }

    private class FlvSource(src: Source) : ForwardingSource(src) {
        private var isHeader = true

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (!isHeader)
                return super.read(sink, byteCount)

            val buf = Buffer()
            if (byteCount < 1024)
                throw IOException("byteCount < 1024")
            val n = super.read(buf, byteCount)
            if (n <= 0)
                return n
            isHeader = false

            val a = buf.readByteArray()
            Timber.d("FLV Header: " + a.joinToString(limit = 5, transform = { "%02x".format(it) }))
            if (n >= 5 &&
                a[0].toInt() == 0x46 && // 'F'
                a[1].toInt() == 0x4c && // 'L'
                a[2].toInt() == 0x56 && // 'V'
                a[3].toInt() == 0x01 && // Always 1
                (a[4].toInt() and 0x05) != 0 // Frags
            ) {
                var newFlags = 0x01 //Video
                if ("audio" in a.toString(Charsets.US_ASCII)) {
                    newFlags = newFlags or 0x04 //+Audio
                }
                Timber.i("Overwrite FLV flags: 0x%02x -> 0x%02x", a[4], newFlags)
                a[4] = newFlags.toByte()
            }
            sink.write(a)
            return n
        }
    }

}