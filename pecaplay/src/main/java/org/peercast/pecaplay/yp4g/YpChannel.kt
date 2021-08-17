package org.peercast.pecaplay.yp4g

import androidx.room.Ignore
import org.peercast.pecaplay.core.app.Yp4gChannel
import java.util.*
import kotlin.collections.HashMap

/**
 * YP4G/index.txtのChannel情報です。
 */
abstract class YpChannel : Yp4gChannel {

    /**再生可能か*/
    open val isPlayable get() = !isEmptyId

    val isEmptyId get() = id == EMPTY_ID

    override fun toString(): String = "${javaClass.simpleName} [$name,$id]"

    /**
     * IdとNameが同じならtrue
     */
    fun equalsIdName(other: YpChannel): Boolean {
        return name == other.name && id == other.id
    }

    @Ignore
    private val tag = HashMap<String, Any?>()

    /**追加プロパティのキャッシュ 検索文など*/
    fun <T> tag(key: String, putValue: YpChannel.() -> T?): T? {
        @Suppress("unchecked_cast")
        return tag.getOrPut(key) { this.putValue() } as T?
    }


    companion object {
        const val EMPTY_ID = "00000000000000000000000000000000" // 0{32}
    }

}