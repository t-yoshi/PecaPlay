package org.peercast.pecaplay.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

object TextUtils {
    fun String.normalize() : String {
        return Normalizer.normalize(this, Normalizer.Form.NFKC)
            .hiragana().lowercase()
    }

    //全角カタカナ -> 全角ひらがな
    private fun String.hiragana(): String {
        val b = StringBuilder(this.length)
        this.forEach { c ->
            when (c) {
                in CharRange('ァ', 'ヶ') -> c + 'あ'.code - 'ア'.code
                else -> c
            }.let(b::append)
        }
        return b.toString()
    }

}