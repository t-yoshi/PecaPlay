package org.peercast.pecaplay.yp4g.net

import androidx.sqlite.db.SupportSQLiteStatement
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.yp4g.Yp4gColumn
import java.util.*

class Yp4gChannelBinder {
    private val m = EnumMap<Yp4gColumn, Any>(Yp4gColumn::class.java)

    fun bindToStatement(statement: SupportSQLiteStatement, vararg columns: Yp4gColumn) {
        columns.forEachIndexed { i, c ->
            when (c.type) {
                String::class.java ->
                    statement.bindString(i + 1, m[c] as String)
                Long::class.java ->
                    statement.bindLong(i + 1, m[c] as Long)
                else ->
                    throw RuntimeException("unknown type ${c.type}")
            }
        }
    }

    fun setYellowPage(yp: YellowPage) {
        m[Yp4gColumn.YpName] = yp.name
        m[Yp4gColumn.YpUrl] = yp.url
    }

    override fun toString(): String {
        return "${javaClass.simpleName}: $m"
    }

    companion object {
        /**@throws IllegalArgumentException*/
        fun parse(line: String): Yp4gChannelBinder {
            val b = Yp4gChannelBinder()
            line.split("<>").also {
                if (it.size != 19)
                    throw IllegalArgumentException("yp4g field length != 19")
            }.zip(Yp4gColumn.values()).forEach { (v, c) ->
                b.m[c] = c.convert(v)
            }
            return b
        }

    }
}