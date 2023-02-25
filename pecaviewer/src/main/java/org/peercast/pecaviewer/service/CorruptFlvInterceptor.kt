package org.peercast.pecaviewer.service

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
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
class CorruptFlvInterceptor : Interceptor {
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

    private class FlvSource(private val src: Source) : Source by src {
        private var isHeader = true

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (!isHeader)
                return src.read(sink, byteCount)

            val buf = Buffer()
            val n = src.read(buf, byteCount)
            if (n <= 0)
                return n
            isHeader = false

            val a = buf.readByteArray()
            Timber.d("FLV Header: " + a.joinToString(limit = 5, transform = { "%02x".format(it) }))
            if (n > 5 && a[0].toInt() == 0x46 && // 'F'
                a[1].toInt() == 0x4c && // 'L'
                a[2].toInt() == 0x56 && // 'V'
                a[3].toInt() == 0x01 && // Always 1
                a[4].toInt() == 0x04 // Audio Only
            ) {
                Timber.i("Overwrite FLV flags: 0x04 -> 0x05")
                a[4] = 0x05 //Audio+Videoに書き換える
            }
            sink.write(a)
            return n
        }
    }

}