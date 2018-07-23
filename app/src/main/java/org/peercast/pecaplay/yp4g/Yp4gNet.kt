package org.peercast.pecaplay.yp4g

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.persistence.db.SupportSQLiteStatement
import android.support.annotation.MainThread
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.SquareUtils
import retrofit2.*
import retrofit2.http.*
import timber.log.Timber
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.ArrayList


class YpIndexLine(line: String) {
    private val fields = EnumMap<Yp4gColumn, Any>(Yp4gColumn::class.java)

    init {
        line.split("<>").let { f ->
            if (f.size != 19)
                throw IllegalArgumentException("yp4g field length != 19")
            (0 until 19).forEach { i ->
                val c = COLUMNS[i]
                fields[c] = c.convert(f[i])
            }
        }
    }

    fun setYellowPage(yp: YellowPage) {
        fields[Yp4gColumn.YpName] = yp.name
        fields[Yp4gColumn.YpUrl] = yp.url
    }

    fun bindTo(statement: SupportSQLiteStatement, columns: List<Yp4gColumn>) {
        columns.forEachIndexed { i, c ->
            val v = fields[c]
            when (v) {
                is String -> statement.bindString(i + 1, v)
                is Int -> statement.bindLong(i + 1, v.toLong())
                is Long -> statement.bindLong(i + 1, v)
                is Float -> statement.bindDouble(i + 1, v.toDouble())
                is Double -> statement.bindDouble(i + 1, v)
                null -> statement.bindNull(i + 1)
                else -> throw IllegalArgumentException("${v.javaClass}")
            }
        }
    }

    companion object {
        private val COLUMNS = Yp4gColumn.values()
    }
}


interface Yp4gService {
    @GET("index.txt")
    fun getIndex(@Query("host") host: String): Call<List<YpIndexLine>>

    @GET("yp4g.xml")
    fun getConfig(): Call<Yp4gConfig>

    @POST("{object}")
    fun speedTest(
            @Path("object", encoded = true) obj: String,
            @Body data: RandomDataBody): Call<ResponseBody>

}

/**速度測定用のランダムデータ*/
class RandomDataBody(
        /**bytes*/
        private val length: Int,
        /**upload速度制限 bytes/sec*/
        private val limit: Int,
        /**0 to 100*/
        private val onProgress: (Int) -> Unit) : RequestBody() {

    private val randomData = ByteArray(10 * 1024)

    init {
        Random().nextBytes(randomData)
    }

    override fun contentType() =
            MediaType.parse("application/octet-stream")

    override fun writeTo(sink: BufferedSink) {
        var sent = 2 //送信済みbytes

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


private object YpIndexLineConverter : Converter<ResponseBody, List<YpIndexLine>> {
    const val MAX_LINE = 1024

    override fun convert(value: ResponseBody): List<YpIndexLine> {
        return value.charStream().buffered().useLines {
            it.take(MAX_LINE).mapIndexedNotNull { i, line ->
                try {
                    YpIndexLine(line)
                } catch (e: IllegalArgumentException) {
                    Timber.w("line=#${i + 1} ${e.cause?.message ?: e.message}")
                    null
                }
            }.toList()
        }
    }
}

private object YpConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(type: Type?, annotations: Array<out Annotation>?,
                                       retrofit: Retrofit?): Converter<ResponseBody, *>? {
        if (type is ParameterizedType &&
                type.rawType === List::class.java &&
                type.actualTypeArguments.contains(YpIndexLine::class.java)) {
            return YpIndexLineConverter
        }
        return null
    }
}

class YellowPageLiveLoader(private val ypLiveData: LiveData<List<YellowPage>>,
                           /**Peca動作ポートを返す*/
                           private val onLoadStart: () -> Int = { 7144 })
    : LiveData<YellowPageLiveLoader.Result>() {

    private val activeCalls = ArrayList<Call<List<YpIndexLine>>>()

    class Result {
        val lines = ArrayList<YpIndexLine>(256)
        val exceptions = HashMap<YellowPage, Throwable>()
    }

    private val observer = Observer<List<YellowPage>> {
        val yellowPages = it ?: emptyList()
        if (!isLoading && yellowPages.isNotEmpty())
            load(yellowPages)
    }

    init {
        ypLiveData.observeForever(observer)
    }

    override fun onActive() {
        ypLiveData.observeForever(observer)
    }

    override fun onInactive() {
        cancel()
        ypLiveData.removeObserver(observer)
    }

    val isLoading get() = activeCalls.isNotEmpty()

    @MainThread
    private fun load(yellowPages: List<YellowPage>) {
        val port = onLoadStart()
        val ret = Result()

        var thread = yellowPages.size

        yellowPages.forEach { yp ->
            val service = SquareUtils.retrofitBuilder()
                    .baseUrl(yp.url)
                    .addConverterFactory(YpConverterFactory)
                    .build()
                    .create(Yp4gService::class.java)

            val call = service.getIndex("host=localhost:$port")

            activeCalls.add(call)

            call.enqueue(object : Callback<List<YpIndexLine>> {
                //Main Thread
                override fun onResponse(call: Call<List<YpIndexLine>>, response: Response<List<YpIndexLine>>) {
                    val lines = response.body() ?: emptyList()
                    lines.forEach { it.setYellowPage(yp) }
                    ret.lines.addAll(lines)
                    onExit(call)
                }

                override fun onFailure(call: Call<List<YpIndexLine>>, t: Throwable) {
                    //Log.e(TAG, "Error -> $t", t)
                    ret.exceptions[yp] = t
                    onExit(call)
                }

                @MainThread
                private fun onExit(c: Call<List<YpIndexLine>>) {
                    activeCalls.remove(c)
                    if (--thread == 0)
                        value = ret
                }
            })
        }
    }

    @MainThread
    fun cancel() {
        if (activeCalls.isEmpty())
            return
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
        value = Result()
    }

}


private const val TAG = "Yp4gNet"