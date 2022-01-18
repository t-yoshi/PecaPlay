package org.peercast.pecaplay.core.io

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

suspend fun Uri.isSiteLocalAddress() : Boolean {
    return host?.let { isSiteLocalAddress(it) } ?: false
}

suspend fun isSiteLocalAddress(ip: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            @Suppress("BlockingMethodInNonBlockingContext")
            InetAddress.getByName(ip).isSiteLocalAddress
        } catch (e: UnknownHostException) {
            isSiteLocalAddressFb(ip)
        }
    }
}

fun Uri.isLoopbackAddress() : Boolean {
    return host?.let { isLoopbackAddress(it) } ?: false
}

fun isLoopbackAddress(ip: String): Boolean {
    return ip in listOf("localhost", "127.0.0.1")
}

private fun isSiteLocalAddressFb(ip: String): Boolean {
    val n = ip.split(".")
        .mapNotNull { it.toUByteOrNull()?.toInt() }
    return when {
        n.size != 4 -> false

        // a)
        n[0] == 192 && n[1] == 168 -> true

        // b)
        n[0] == 172 && n[1] in 16..31 -> true

        // c)
        n[0] == 10 -> true

        else -> false
    }
}