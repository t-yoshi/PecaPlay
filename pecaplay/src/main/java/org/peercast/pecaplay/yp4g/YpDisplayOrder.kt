package org.peercast.pecaplay.yp4g

import java.util.*
import kotlin.Comparator

enum class YpDisplayOrder(cmp: Comparator<YpChannel>) {
    LISTENERS_DESC(YpComparators.LISTENERS_REV),
    LISTENERS_ASC(YpComparators.LISTENERS),
    AGE_DESC(YpComparators.AGE_REV),
    AGE_ASC(YpComparators.AGE),
    NONE(Comparator { _, _ -> throw RuntimeException() });

    val comparator = YpComparators.chained(
        YpComparators.NOTICE,
        cmp, YpComparators.LISTENERS_REV,
        YpComparators.AGE_REV, YpComparators.NAME
    )

    companion object {
        private val DEFAULT = LISTENERS_DESC

        fun fromName(name: String?) =
            values().firstOrNull {
                it.name.equals(name, true)
            } ?: DEFAULT

        fun fromOrdinal(o: Int) = values().getOrNull(o) ?: DEFAULT

        private object YpComparators {
            //お知らせを常に下に持っていく
            val NOTICE = Comparator<YpChannel> { a, b ->
                val infoA = a.listeners < -1
                val infoB = b.listeners < -1
                if (infoA && infoB)
                    0
                else
                    infoA.compareTo(infoB)
            }

            //リスナー数で比較
            val LISTENERS = Comparator<YpChannel> { a, b ->
                a.listeners.compareTo(b.listeners)
            }

            val LISTENERS_REV = Collections.reverseOrder(LISTENERS)

            //Ch名で比較
            val NAME = Comparator<YpChannel> { a, b ->
                a.name.compareTo(b.name)
            }


            //配信時間で比較
            val AGE: Comparator<YpChannel> = Comparator { a, b ->
                a.ageAsMinutes.compareTo(b.ageAsMinutes)
            }

            val AGE_REV = Collections.reverseOrder(AGE)

            //複数のコンパレータをつなげる
            fun chained(vararg cmps: Comparator<YpChannel>): Comparator<YpChannel> {
                return Comparator { a, b ->
                    cmps.map {
                        it.compare(a, b)
                    }.firstOrNull { it != 0 } ?: 0
                }
            }
        }

        private val YpChannel.ageAsMinutes: Int
            get() = tag("YpDisplayOrder#ageAsMinutes") {
                "(\\d+):(\\d\\d)\\s*$".toRegex().find(age)
                    ?.let { m ->
                        m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                    }
            } ?: 0
    }
}