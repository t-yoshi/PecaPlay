package org.peercast.pecaplay

import org.junit.Test
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.yp4g.Yp4gField
import org.peercast.pecaplay.yp4g.createYp4gService
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
    fun print_Yp4gField_field_name() {

        val parameters = Yp4gField::class.constructors.first().parameters
        var s = parameters.joinToString(
            prefix = "val NAMES = listOf(\"",
            separator = "\",\"",
            postfix = "\")",
            transform = { it.name!! }
        )
        println(s)



        s = parameters.joinToString(
            prefix = "fun toStringValues() = listOf(",
            separator = ", ",
            postfix = ")",
            transform = {
                when {
                    it.type.javaType == String::class.java -> "${it.name}"
                    else -> "${it.name}.toString()"
                }
            }
        )
        println(s)
    }

    @Test
    fun loadYpTest() {
        createYp4gService(fakeYp)
            .getIndex("localhost:7144")
            .execute().body()?.forEach {
                println(it.toString())
            }
    }

    @Test
    fun rxLoadYpTest() {
/*
        listOf(errYp, fakeYp, sp, tp).map { yp->
            createYp4gService(yp).getIndexRx("localhost:7144")
                .subscribeOn(Schedulers.io())
                .map {res->
                    val u = res.raw().request().url().toString()
                    res.body()?.map { it.create(yp, u) } ?: emptyList()
                }
                .onErrorReturn {th->
                    println("error: $yp")
                    th.printStackTrace()
                    //errorHandler
                    emptyList()
                }
        }.let {singles->
            Single.zip(singles){a->
                @Suppress("unchecked_cast")
                a.map { it as List<Yp4gRawField>  }
                    .flatten()
            }
        }.let {
            it.subscribe { lines->
                println("OK:")
                lines.forEach {
                    println(it)
                }
            }
        }
/ *
        val x1 = createYp4gService(fakeYp)
            .getIndexRx("localhost:7144").map {
                val u = it.raw().request().url().toString()
                it.body()?.map { it.create(fakeYp, u) } ?: emptyList()
            }
        val x2 = createYp4gService(sp)
            .getIndexRx("localhost:7144").map {
                val u = it.raw().request().url().toString()
                it.body()?.map { it.create(sp, u) } ?: emptyList()
            }
            .onErrorReturn { emptyList() }
        Single.zip<List<Yp4gRawField>, List<Yp4gRawField>>(listOf(x1, x2)){
            println("xx ${it.toList()}")
            it.map{ it as List<Yp4gRawField> }.flatten()
        }.subscribe ({
            println(it)
        }){
            it.printStackTrace()
        }

        Observable.combineLatest(
            x1.toObservable(),
            x2.toObservable(),
            BiFunction<List<Yp4gRawField>, List<Yp4gRawField>, List<Yp4gRawField>> { t1, t2 -> t1+t2 })
            .onErrorReturn {
                it.printStackTrace()
                emptyList()
            }
            .subscribe({
                it.forEach {
                    println(it)
                }
            }){
                it.printStackTrace()
            }
*/

/*
        createYp4gService(fakeYp)
            .getIndexRx("localhost:7144")
//            .subscribeOn(
//                Schedulers.io()
//            )

            .subscribe({
                val u = it.raw().request().url().toString()
                val channels = it.body()?.map { it.create(fakeYp, u) }
                println(channels)
            }){
                it.printStackTrace()
            }
*/
    }

    @Test
    fun uploadTest() {
        /*
        val tester = Yp4gSpeedTester(fakeYp)
        var isFinish = false

        tester.loadConfig {
            println("loadConfig $it: ${tester.config}")

            if (it) {
                tester.startTest({
                    println("progress=$it")

                }) {
                    println("success=$it")
                    isFinish = true
                }
            } else {
                isFinish = true
            }
        }

        while (!isFinish)
            Thread.sleep(100)
            */
    }
}