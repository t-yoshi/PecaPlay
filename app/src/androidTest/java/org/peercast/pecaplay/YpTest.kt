package org.peercast.pecaplay

import android.arch.lifecycle.MutableLiveData
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.peercast.pecaplay.yp4g.RandomDataBody

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class YpTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        Assert.assertEquals("org.peercast.pecaplay", appContext.packageName)
    }


    @Test
    fun post() {
        val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .build()

        val progress = MutableLiveData<Int>().apply {
            observeForever {
                print("$it,")
            }
        }

        val reqBody = RandomDataBody(10 * 1024, 100*1024, progress)

        val req = Request.Builder()
                .url("http://httpbin.org/post")
                .post(reqBody)
                .build()

        val res = client.newCall(req).execute()
        res.body().let {
            it?.let {
                print(it)
            }
            Log.d("xx", "$it")
        }
    }


}
