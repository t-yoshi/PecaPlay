package org.peercast.pecaplay.yp4g.net

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream

/**
YP4G仕様
http://mosax.sakura.ne.jp/yp4g/fswiki.cgi?page=YP4G%BB%C5%CD%CD
 */
class Yp4gConfig(m: Map<String, String>) {
    //   /yp4g/yp@name=Xxx yellow Pages
    val name = m["/yp4g/yp@name"] ?: "(none)"

    val host = Host(m)

    val uptest = UpTest(m)

    val uptest_srv = UpTestServer(m)

    class Host(m: Map<String, String>) {
        //   /yp4g/host@ip=123.145.167.189
        //   /yp4g/host@over=0
        //   /yp4g/host@port_open=0
        //   /yp4g/host@speed=0

        val ip = m["/yp4g/host@ip"] ?: ""

        private val port_open = m["/yp4g/host@port_open"]?.toIntOrNull() ?: 0

        val speed = m["/yp4g/host@speed"]?.toIntOrNull() ?: 0

        private val over = m["/yp4g/host@over"]?.toIntOrNull() ?: 0

        val isPortOpen get() = port_open == 1
        val isOver get() = over == 1

        override fun toString(): String {
            return "ip=$ip, isPortOpen=$isPortOpen, speed=$speed, isOver=$isOver"
        }
    }

    class UpTest(m: Map<String, String>) {
        //   /yp4g/uptest@checkable=0
        //   /yp4g/uptest@remain=0

        private val checkable = m["/yp4g/uptest@checkable"]?.toIntOrNull() ?: 0

        val remain = m["/yp4g/uptest@remain"]?.toIntOrNull() ?: 0

        val isCheckable get() = checkable == 1

        override fun toString(): String {
            return "isCheckable=$isCheckable, remain=$remain"
        }
    }

    class UpTestServer(m: Map<String, String>) {
        //   /yp4g/uptest_srv@addr=xxx.orz.abc
        //   /yp4g/uptest_srv@enabled=0
        //   /yp4g/uptest_srv@interval=15
        //   /yp4g/uptest_srv@limit=4500
        //   /yp4g/uptest_srv@object=/uptest.cgi
        //   /yp4g/uptest_srv@port=443
        //   /yp4g/uptest_srv@post_size=250

        val addr = m["/yp4g/uptest_srv@addr"] ?: ""

        val port = m["/yp4g/uptest_srv@port"]?.toIntOrNull() ?: 0

        val `object` = m["/yp4g/uptest_srv@object"] ?: ""

        /**KBytes */
        var postSize = m["/yp4g/uptest_srv@post_size"]?.toIntOrNull() ?: 0

        var limit = m["/yp4g/uptest_srv@limit"]?.toIntOrNull() ?: 0

        var interval = m["/yp4g/uptest_srv@interval"]?.toIntOrNull() ?: 0

        private var enabled = m["/yp4g/uptest_srv@enabled"]?.toIntOrNull() ?: 0

        val isEnabled get() = enabled == 1

        override fun toString(): String {
            return "addr=$addr, port=$port, object=$`object`, postSize=$postSize, limit=$limit, interval=$interval, isEnabled=$isEnabled"
        }
    }

    private class Parser {
        private val factory = XmlPullParserFactory.newInstance()

        fun parseXml(ins: InputStream): Yp4gConfig {
            val m = HashMap<String, String>()

            val parser = factory.newPullParser()
            try {
                parser.setInput(ins, null)
                val tags = ArrayList<String>()

                while (true) {
                    when (parser.next()) {
                        XmlPullParser.START_DOCUMENT -> {
                        }
                        XmlPullParser.START_TAG -> {
                            val name = "/" + checkNotNull(parser.name)
                            if (tags.isEmpty() && name != "/yp4g")
                                throw IOException("Root element is not <yp4g>")
                            tags.add(name)
                            m.putAll(
                                parser.iterateAttributes(tags.joinToString("") + "@")
                            )
                        }
                        XmlPullParser.END_TAG -> {
                            tags.removeLast()
                        }
                        XmlPullParser.END_DOCUMENT -> {
                            break
                        }
                    }
                }
//                TreeMap(m).forEach {
//                    Timber.d("--> $it")
//                }

                return Yp4gConfig(m)
            } catch (e: XmlPullParserException) {
                throw IOException("incorrect xml format.", e)
            }
        }

        private fun XmlPullParser.iterateAttributes(keyPrefix: String): Iterable<Pair<String, String>> {
            return (0 until attributeCount).map {
                keyPrefix + getAttributeName(it) to getAttributeValue(it)
            }
        }
    }

    override fun toString(): String {
        return "Yp4gConfig(name=$name, host=[$host], uptest=[$uptest], uptest_srv=[$uptest_srv])"
    }

    companion object {
        fun parse(ins: InputStream) = Parser().parseXml(ins)

        val NONE = Yp4gConfig(emptyMap())
    }
}

