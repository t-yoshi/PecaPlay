package org.peercast.pecaplay.yp4g

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.Path
import org.simpleframework.xml.Root

/**
YP4G仕様
http://mosax.sakura.ne.jp/yp4g/fswiki.cgi?page=YP4G%BB%C5%CD%CD
 */
@Root(name = "yp4g")
class Yp4gConfig {
    @field:[Path("yp") Attribute]
    var name = "(none)"
        private set

    @field:Element
    var host = Host()
        private set

    @field:Element
    var uptest = UpTest()
        private set

    @field:Element
    var uptest_srv = UpTestServer()
        private set

    class Host {
        @field:Attribute
        var ip: String = ""
            private set

        @field:Attribute
        private var port_open = 0

        @field:Attribute
        var speed = 0
            private set

        @field:Attribute
        private var over = 0

        val isPortOpen get() = port_open == 1
        val isOver get() = over == 1

        override fun toString(): String {
            return "ip=$ip, isPortOpen=$isPortOpen, speed=$speed, isOver=$isOver"
        }
    }

    class UpTest {
        @field:Attribute
        private var checkable = 0

        @field:Attribute
        var remain = 0
            private set

        val isCheckable get() = checkable == 1

        override fun toString(): String {
            return "isCheckable=$isCheckable, remain=$remain"
        }
    }

    class UpTestServer {
        @field:Attribute
        var addr = ""
            private set

        @field:Attribute
        var port = 0
            private set

        @field:Attribute
        var `object` = ""
            private set

        /**KBytes */
        @field:Attribute(name = "post_size")
        var postSize = 0
            private set

        @field:Attribute
        var limit = 0
            private set

        @field:Attribute
        var interval = 0
            private set

        @field:Attribute
        private var enabled = 0

        val isEnabled get() = enabled == 1

        override fun toString(): String {
            return "addr=$addr, port=$port, object=$`object`, postSize=$postSize, limit=$limit, interval=$interval, isEnabled=$isEnabled"
        }
    }

    companion object {
        val NONE = Yp4gConfig()
    }

    override fun toString(): String {
        return "Yp4gConfig(name=$name, host=[$host], uptest=[$uptest], uptest_srv=[$uptest_srv])"
    }
}