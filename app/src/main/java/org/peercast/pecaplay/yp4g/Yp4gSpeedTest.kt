package org.peercast.pecaplay.yp4g

import android.content.Context
import androidx.annotation.WorkerThread
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.util.SquareUtils
import timber.log.Timber
import java.io.IOException


class Yp4gSpeedTester(val yp: YellowPage) {
    var config = NONE_CONFIG
        private set
    private var error = ""

    suspend fun loadConfig(useCache: Boolean = true): Boolean {
        when {
            useCache && error != "" -> return false
            useCache && config !== NONE_CONFIG -> return true
        }

        val service = SquareUtils.retrofitBuilder()
            .baseUrl(yp.url)
            .addConverterFactory(SquareUtils.SIMPLE_XML_CONVERTER_FACTORY)
            .build()
            .create(Yp4gService::class.java)

        return try {
            config = service.getConfig().body() ?: throw IOException("no config exists.")
            Timber.i("loadConfig OK: $config")
            true
        } catch (e: Exception) {
            //RuntimeException(cause=XmlPullParserException) または IOException
            config = Yp4gConfig.NONE
            error = e.toString()
            Timber.e(e, "loadConfig Failed: ")
            false
        }
    }

    suspend fun startTest(@WorkerThread onProgress: (Int) -> Unit): Boolean {
        Timber.d("startTest $config")

        val u = config.uptest_srv.run { "http://$addr:$port/" }
        val service = SquareUtils.retrofitBuilder()
            .baseUrl(u)
            .build()
            .create(Yp4gService::class.java)

        val reqBody = RandomDataBody(
            config.uptest_srv.postSize * 1024,
            config.uptest_srv.limit * 1024 / 8,
            onProgress
        )

        Timber.d("config=$config post=$reqBody")

        val obj = config.uptest_srv.`object`

        return try {
            val response = service.speedTest(obj, reqBody)
            Timber.i("SpeedTest OK: %s", response.body())
            //Configの再読込
            loadConfig(false)
        } catch (e: IOException) {
            Timber.e(e, "SpeedTest Failed")
            error = e.toString()
            false
        }
    }

    fun getStatus(c: Context): String {
        if (error != "")
            return error

        var s = "${config.host.speed}kbps"
        if (config.host.isOver)
            s += " over "
        if (!config.host.isPortOpen) {
            s += " (${c.getString(R.string.closed_port)})"
        }
        return s
    }

    override fun toString() = yp.name

    companion object {
        private const val TAG = "YpTester"
        private val NONE_CONFIG = Yp4gConfig.NONE
    }
}

