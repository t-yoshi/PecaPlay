package org.peercast.pecaplay

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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

}
