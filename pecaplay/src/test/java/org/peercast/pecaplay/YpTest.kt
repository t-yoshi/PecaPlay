package org.peercast.pecaplay

import org.junit.Test
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.yp4g.net.Yp4gConfig
import timber.log.Timber


class YpTest {
    val fakeYp = YellowPage("Fake YP", "http://localhost:8000/")
    val errYp = YellowPage("Error YP", "http://xxxxxxxxx/")
    val sp = YellowPage("SP", "http://bayonet.ddo.jp/sp/")
    val tp = YellowPage("TP", "http://temp.orz.hm/yp/")

    init {
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                println("${tag ?: ""} $message")
                t?.printStackTrace()
            }
        })
    }

    @Test
    fun testYpConfig() {
        val s = """
            <yp4g>
            <yp name="Xxx yellow Pages"/>
            <host ip="123.145.167.189" port_open="0" speed="0" over="0"/>
            <uptest checkable="0" remain="0"/>
            <uptest_srv addr="xxx.orz.abc" port="443" object="/uptest.cgi" post_size="250" limit="4500" interval="15" enabled="0"/>
            </yp4g>
        """.trimIndent()
        val config = Yp4gConfig.parse(s.byteInputStream())

//        --> /yp4g/host@ip=123.145.167.189
//        --> /yp4g/host@over=0
//        --> /yp4g/host@port_open=0
//        --> /yp4g/host@speed=0
//        --> /yp4g/uptest@checkable=0
//        --> /yp4g/uptest@remain=0
//        --> /yp4g/uptest_srv@addr=xxx.orz.abc
//        --> /yp4g/uptest_srv@enabled=0
//        --> /yp4g/uptest_srv@interval=15
//        --> /yp4g/uptest_srv@limit=4500
//        --> /yp4g/uptest_srv@object=/uptest.cgi
//        --> /yp4g/uptest_srv@port=443
//        --> /yp4g/uptest_srv@post_size=250
//        --> /yp4g/yp@name=Xxx yellow Pages

        println(config)
    }

}