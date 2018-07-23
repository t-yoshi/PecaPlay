package org.peercast.pecaplay

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Test
import org.peercast.pecaplay.yp4g.RandomDataBody
import org.peercast.pecaplay.yp4g.Yp4gConfig
import org.peercast.pecaplay.yp4g.Yp4gService
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.Path
import org.simpleframework.xml.Root
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.util.*
import java.util.concurrent.TimeUnit


@Root(name = "yp4g")
class Yp4gConfigK {
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

    override fun toString() = ObjectUtils.toStringReflect(Yp4gConfigK::class, this)

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

        //override fun toString() = ObjectUtils.toStringReflect(Host::class, this)
    }

    class UpTest {
        @field:Attribute
        private var checkable = 0

        @field:Attribute
        var remain = 0
            private set

        val isCheckable get() = checkable == 1

        //override fun toString() = ObjectUtils.toStringReflect(UpTest::class, this)
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

        //override fun toString() = ObjectUtils.toStringReflect(UpTestServer::class, this)
    }

    companion object {
        val NONE = Yp4gConfigK()
    }
}

/*

@Root(name = "yp4g", strict = true)
class TestYp4gConfig {
    @field:Path("yp")
    lateinit var name: String

    lateinit var host: Host
        @Element set
        @Element get

    lateinit var upTest: UpTest
        @Element(name = "uptest") set
        @Element(name = "uptest") get

    lateinit var upTestServer: UpTestServer
        @Element(name = "uptest_srv") set
        @Element(name = "uptest_srv") get


    class Host {
        lateinit var ip: String
            @Attribute set
            @Attribute get
        var port_open = false
            @Attribute set
            @Attribute get
        var speed = 0
            @Attribute set
            @Attribute get
        var over = 0
            @Attribute set
            @Attribute get
    }

    class UpTest {
        var checkable = 0
            @Attribute set
            @Attribute get
        var remain = 0
            @Attribute set
            @Attribute get
    }

    class UpTestServer {
        lateinit var addr: String
            @Attribute set
            @Attribute get
        var port = 0
            @Attribute set
            @Attribute get
        lateinit var `object`: String
            @Attribute set
            @Attribute get
        var post_size = 0
            @Attribute set
            @Attribute get
        var limit = 0
            @Attribute set
            @Attribute get
        var interval = 0
            @Attribute set
            @Attribute get
        var enabled = false
            @Attribute set
            @Attribute get
    }
}

*/

class YpUnitTest {
    private val client = NetUtils.httpClient.newBuilder()
            .writeTimeout(100, TimeUnit.SECONDS)

            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()

    private val progress: (Int) -> Unit = {
        //throw Throwable("")
        println("${Date()} $it")
    }

    @Test
    fun post() {
        val reqBody = RandomDataBody(100 * 1024, 3 * 1024, progress)

        val req = Request.Builder()
                .url("http://httpbin.org/post")
                .post(reqBody)
                .build()

        val res = client.newCall(req).execute()
        res.body().let {
            it?.let {
                print(it.string())
            }
            println(it)
        }
    }

    @Test
    fun testToStringReflection() {
        data class A(val a: Int = 1)
        open class B {
            val base = 1234
        }

        class X : B() {
            val x = 1
            private val y = 1
            var z = 1

            val a = A()
            val xx: String? = null
        }

        val x = X()
        println(ObjectUtils.toStringReflect(X::class, x))
    }

    @Test
    fun xxx() {
        val cfg = """<?xml version="1.0" encoding="utf-8" ?>
<yp4g>
	<yp name="SP" />
	<host ip="123.96.56.110" port_open="0" speed="0" over="0" />
	<uptest checkable="1" remain="0" />
	<uptest_srv addr="bayonet.ddo.jp" port="444" object="/sp/uptest.cgi" post_size="250" limit="3000" interval="15" enabled="1" />
</yp4g>
        """.trimIndent()
        val p = org.simpleframework.xml.core.Persister()
        val o = p.read(Yp4gConfigK::class.java, cfg)

        print(o)
    }

    @Test
    fun test() {
        val cfg = """<?xml version="1.0" encoding="utf-8" ?>
<yp4g>
	<yp name="SP" />
	<host ip="220.96.56.110" port_open="0" speed="0" over="0" />
	<uptest checkable="0" remain="0" />
	<uptest_srv addr="bayonet.ddo.jp" port="444" object="/sp/uptest.cgi" post_size="250" limit="3000" interval="15" enabled="1" />
</yp4g>
        """.trimIndent()

        val p = org.simpleframework.xml.core.Persister()
        val o = p.read(Yp4gConfig::class.java, cfg)


        val u = HttpUrl.Builder()
                .scheme("http")
                .host(o.uptest_srv.addr)
                .port(o.uptest_srv.port)
                //.addPathSegments("/")
                .build()

        val s = Retrofit.Builder()
                .baseUrl(u)
                .client(client)
                .addConverterFactory(SimpleXmlConverterFactory.create())
                .build()
                .create(Yp4gService::class.java)

        val res = s.speedTest(
                o.uptest_srv.`object`,
                RandomDataBody(o.uptest_srv.postSize * 1024, 100 * 1024 / 8, progress)
        ).execute()

        println("errorbody: " + res.errorBody()?.string())

        res.body().let {
            it?.let {
                print(it)
            }
            println(it)
        }
    }

    @Test
    fun testUp() {
        val service = NetUtils.retrofitBuilder()
                .baseUrl("http://httpbin.org/")
                .build()
                .create(Yp4gService::class.java)

        val reqBody = RandomDataBody(
                256 * 1024,
                100 * 1024 / 8,
                { println(it) }
        )

        val obj = "post"
        service.speedTest(obj, reqBody).also {
            it.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    println("SpeedTest OK: " + response.body()?.string())
                    System.exit(0)
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    println(t)
                    //activeCall = null
                }
            })
        }
        Thread.sleep(110 * 1000)
    }

}

const val TAG = "Test"