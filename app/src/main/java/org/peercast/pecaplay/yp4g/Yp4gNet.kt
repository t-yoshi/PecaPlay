package org.peercast.pecaplay.yp4g

import androidx.sqlite.db.SupportSQLiteStatement
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import org.peercast.pecaplay.util.SquareUtils
import org.peercast.pecaplay.app.YellowPage
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.*
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*


/**index.txtの行をparseしたもの*/
typealias Yp4gRawField = EnumMap<Yp4gColumn, String>

fun Yp4gRawField.bindTo(statement: SupportSQLiteStatement, columns: List<Yp4gColumn>) {
    columns.forEachIndexed { i, c ->
        statement.bindString(i + 1, getValue(c))
    }
}


class Yp4gRawFieldFactory(
    /**ypを注入して完成
     * @throws Yp4gFormatException*/
    val create: (yp: YellowPage, url: String) -> Yp4gRawField
)

private object Yp4gRawFieldFactoryConverter : Converter<ResponseBody, List<Yp4gRawFieldFactory>> {
    private fun parseLine(num: Int, line: String): Yp4gRawFieldFactory {
        return try {
            val m = Yp4gRawField(Yp4gColumn::class.java)
            line.split("<>").also {
                if (it.size != 19)
                    throw Yp4gFormatException("yp4g field length != 19")
            }.zip(Yp4gColumn.values()).forEach { (v, c) ->
                try {
                    m[c] = c.convert(v)
                } catch (e: Yp4gFormatException) {
                    throw Yp4gFormatException("invalid value $c: ${e.message}")
                }
            }

            Yp4gRawFieldFactory { yp, url ->
                m[Yp4gColumn.YpName] = yp.name
                m[Yp4gColumn.YpUrl] = yp.url
                m
            }
        } catch (e: Yp4gFormatException) {
            Yp4gRawFieldFactory { yp, url ->
                throw Yp4gFormatException("$url #${num + 1}: ${e.message}")
            }
        }
    }

    override fun convert(body: ResponseBody): List<Yp4gRawFieldFactory> {
        return body.use {
            it.charStream().buffered()
                .lineSequence().take(MAX_LINE)
                .mapIndexed(::parseLine)
                .toList()
        }
    }

    private const val MAX_LINE = 1024
}


interface Yp4gService {
    @GET("index.txt")
    fun getIndex(@Query("host") host: String): Call<List<Yp4gRawFieldFactory>>

    @GET("yp4g.xml")
    fun getConfig(): Call<Yp4gConfig>

    @POST("{object}")
    fun speedTest(
        @Path("object", encoded = true) obj: String,
        @Body data: RandomDataBody
    ): Call<ResponseBody>

}

/**速度測定用のランダムデータ*/
class RandomDataBody(
    /**bytes*/
    private val length: Int,
    /**upload速度制限 bytes/sec*/
    private val limit: Int,
    /**0 to 100*/
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    private val randomData = ByteArray(10 * 1024)

    init {
        Random().nextBytes(randomData)
    }

    override fun contentType() =
        MediaType.parse("application/octet-stream")

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

private object YpConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type?, annotations: Array<out Annotation>?,
        retrofit: Retrofit?
    ): Converter<ResponseBody, *>? {

        if (type is ParameterizedType &&
            getRawType(type) === List::class.java &&
            getParameterUpperBound(0, type) === Yp4gRawFieldFactory::class.java
        ) {
            return Yp4gRawFieldFactoryConverter
        }

        return null
    }
}

fun createYp4gService(yp: YellowPage): Yp4gService =
    SquareUtils.retrofitBuilder()
        .baseUrl(yp.url)
        .addConverterFactory(YpConverterFactory)
        .build()
        .create(Yp4gService::class.java)



private const val TAG = "Yp4gNet"