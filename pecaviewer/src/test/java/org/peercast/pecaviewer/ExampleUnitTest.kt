package org.peercast.pecaviewer

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.peercast.pecaviewer.chat.net.openBoardConnection
import org.peercast.pecaviewer.util.ISquareHolder
import timber.log.Timber

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    init {
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("$tag $message")
                t?.printStackTrace()
            }
        })
        startKoin {
            modules(module {
                single<ISquareHolder> { TestSquareHolder() }
            })
        }
    }

    private class TestSquareHolder : ISquareHolder {
        override val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
        override val moshi: Moshi = Moshi.Builder().build()
    }

    //http://hibino.ddo.jp/bbs/test/read.cgi/peca/1552237443/
    //https://jbbs.shitaraba.net/bbs/rawmode.cgi/game/59608/1552136407/l50
    //http://hibino.ddo.jp/bbs/peca/head.txt
    //http://hibino.ddo.jp/bbs/peca/subject.txt
    //http://hibino.ddo.jp/bbs/peca/dat/1552669728.dat
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun bbsTest() {

        runBlocking {
            val u1 = "http://2chcrew.geo.jp/ze/test/read.cgi/tete/1468154452/1-100"
            val u2 = "http://hibino.ddo.jp/bbs/test/read.cgi/peca/1549381106/l50"
            val u3 = "http://hibino.ddo.jp/bbs/peca/test/read.cgi/peca/1553274401/"
            val u4 = "https://stamp.archsted.com/125"
            val u5 = "http://peercast.s602.xrea.com/test/read.cgi/bbs/1472558865/l50"
            val u6 = "http://komokomo.ddns.net/test/read.cgi/peercast/1582439674/"
            val u7 = "http://bbs.jpnkn.com/test/read.cgi/king7144/1584886487"

            val conn = openBoardConnection(u7)
            conn.loadThreads().forEach {
                println(it)
            }
        }


    }
}
