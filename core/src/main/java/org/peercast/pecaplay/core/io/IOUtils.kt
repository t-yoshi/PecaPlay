package org.peercast.pecaplay.core.io

import android.content.res.Resources
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * ソケット関連のIOExceptionからローカライズされたOSのエラーメッセージを得る。
 * @see FileNotFoundException
 * @see SocketTimeoutException
 * @see UnknownHostException
 * */
fun IOException.localizedSystemMessage(): String {
    val name = when (this) {
        is FileNotFoundException -> "httpErrorFileNotFound"
        is SocketTimeoutException -> "httpErrorTimeout"
        is UnknownHostException -> "httpErrorLookup"
        else -> null
    }
    return name?.run {
        val res = Resources.getSystem()
        val id = res.getIdentifier(name, "string", "android")
        res.getString(id)
    } ?: localizedMessage ?: message ?: toString()
}
