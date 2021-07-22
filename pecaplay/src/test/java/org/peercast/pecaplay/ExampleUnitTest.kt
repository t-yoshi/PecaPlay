package org.peercast.pecaplay

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    @get:Rule
//    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Test
    fun addition_isCorrect() {
        //assertEquals(4, 2 + 2)
        assert(listOf(1, "!").equals(listOf(1, "!")))
    }

    @Test
    fun z() {
        class CO() {
            var x1 = 1
            lateinit var x2: String
        }

        val observer = Observer<CO> {
            println("-> $it")
        }
        val ldb = CombinedLiveDataBuilder2(CO())
        val ld1 = MutableLiveData<Int>()
        val ld2 = MutableLiveData<String>()

        val co = CO()
        //co::x1.set()

        ldb.add(CO::x1, ld1)
            .add(CO::x2, ld2)
            .build().observeForever(observer)
        ld1.observeForever {
            println("...$it")
        }
        println("..")
        Thread.sleep(1000)
    }
}
