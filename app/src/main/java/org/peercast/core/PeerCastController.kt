package org.peercast.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.*
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * PeerCast for Androidをコントロールする。
 *
 * @licenses Dual licensed under the MIT or GPL licenses.
 * @author (c) 2019, T Yoshizawa
 * @version 2.2.1
 */

class PeerCastController private constructor(private val appContext: Context) {

    private var serverMessenger: Messenger? = null
    private val eventListeners = ArrayList<EventListener>()

    val isConnected: Boolean
        get() = serverMessenger != null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, binder: IBinder) {
            // Log.d(TAG, "onServiceConnected!");
            serverMessenger = Messenger(binder)
            eventListeners.forEach { it.onConnectService(this@PeerCastController) }
        }

        override fun onServiceDisconnected(arg0: ComponentName?) {
            // OSにKillされたとき。
            // Log.d(TAG, "onServiceDisconnected!");
            serverMessenger = null
            eventListeners.forEach { it.onDisconnectService(this@PeerCastController) }
        }
    }

    fun addEventListener(listener: EventListener) {
        if (listener in eventListeners)
            return
        if (isConnected)
            listener.onConnectService(this)
        eventListeners += listener
    }

    fun removeEventListener(listener: EventListener) {
        eventListeners -= listener
    }

    /**
     * 「PeerCast for Android」がインストールされているか調べる。
     *
     * @return "org.peercast.core" がインストールされていればtrue。
     */
    val isInstalled: Boolean
        get() {
            return try {
                appContext.packageManager.getApplicationInfo(PKG_PEERCAST, 0)
                true
            } catch (e: NameNotFoundException) {
                false
            }
        }

    interface EventListener {
        /**
         * bindService後にコネクションが確立されると呼ばれます。
         */
        fun onConnectService(controller: PeerCastController)

        /**
         * unbindServiceを呼んだ後、もしくはOSによってサービスがKillされたときに呼ばれます。
         */
        fun onDisconnectService(controller: PeerCastController)
    }

    /**
     * ピアキャスの動作ポートを取得します。
     *  **/
    suspend fun getsProperties() = Properties(
        sendCommandAwait(MSG_GET_APPLICATION_PROPERTIES)
    )

    /**現在アクティブなチャンネルの情報を取得します。*/
    suspend fun getChannels(): List<Channel> {
        return linkedBundleToList(
            sendCommandAwait(MSG_GET_CHANNELS), ::Channel
        )
    }

    /**通信量の状況を取得します。*/
    suspend fun getStats(): Stats {
        return Stats(sendCommandAwait(MSG_GET_STATS))
    }

    private suspend fun sendCommandAwait(msgId: Int): Bundle = suspendCancellableCoroutine { co ->
        val cb = Handler.Callback { msg ->
            if (!co.isCancelled) {
                co.resume(msg.data)
            }
            true
        }
        val msg = Message.obtain(null, msgId)
        msg.replyTo = Messenger(Handler(Looper.getMainLooper(), cb))
        try {
            serverMessenger?.send(msg) ?: throw IllegalStateException("service not connected.")
        } catch (e: RemoteException) {
            if (!co.isCancelled) {
                co.resumeWithException(e)
            }
        }
    }


    private fun sendChannelCommand(msgCmdId: Int, channel_id: Int) {
        val msg = Message.obtain(null, msgCmdId, channel_id, 0)
        try {
            serverMessenger?.send(msg) ?: throw IllegalStateException("service not connected.")
        } catch (e: RemoteException) {
            Log.e(TAG, "msgCmdId=$msgCmdId", e)
        }
    }

    /**
     * Bumpして再接続する。
     * @param channel_id 対象のchannel_id
     */
    fun bumpChannel(channel_id: Int) = sendChannelCommand(MSG_CMD_CHANNEL_BUMP, channel_id)


    /**
     * チャンネルを切断する。
     * @param channel_id 対象のchannel_id
     */
    fun disconnectChannel(channel_id: Int) = sendChannelCommand(MSG_CMD_CHANNEL_DISCONNECT, channel_id)


    /**
     * チャンネルのキープと解除を設定する。
     * @param channel_id 対象のchannel_id
     * @param b
     */
    fun setChannelKeep(channel_id: Int, b: Boolean) {
        if (b)
            sendChannelCommand(MSG_CMD_CHANNEL_KEEP_YES, channel_id)
        else
            sendChannelCommand(MSG_CMD_CHANNEL_KEEP_NO, channel_id)
    }

    /**
     * 指定したServentを切断する。
     */
    fun disconnectServent(servent_id: Int) {
        if (serverMessenger == null)
            throw IllegalStateException("service not connected.")

        val msg = Message.obtain(
            null, MSG_CMD_SERVENT_DISCONNECT,
            servent_id, 0
        )
        try {
            serverMessenger!!.send(msg)
        } catch (e: RemoteException) {
            Log.e(TAG, "MSG_CMD_SERVENT_DISCONNECT", e)
        }

    }

    /**
     * [Context.bindService]を呼び、PeerCastのサービスを開始する。
     *
     * @return
     */
    fun bindService(): Boolean {
        if (!isInstalled) {
            Log.e(TAG, "PeerCast not installed.")
            return false
        }
        val intent = Intent(CLASS_NAME_PEERCAST_SERVICE)
        // NOTE: LOLLIPOPからsetPackage()必須
        intent.setPackage(PKG_PEERCAST)
        return appContext.bindService(
            intent, serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    /**
     * [Context.unbindService]を呼ぶ。 他からもbindされていなければPeerCastサービスは終了する。
     *
     * @return
     */
    fun unbindService() {
        if (!isConnected)
            return
        appContext.unbindService(serviceConnection)
        if (serverMessenger != null)
            serviceConnection.onServiceDisconnected(null)
    }

    companion object {
        internal const val MSG_GET_APPLICATION_PROPERTIES = 0x00
        internal const val MSG_GET_CHANNELS = 0x01
        internal const val MSG_GET_STATS = 0x02
        internal const val MSG_CMD_CHANNEL_BUMP = 0x10
        internal const val MSG_CMD_CHANNEL_DISCONNECT = 0x11
        internal const val MSG_CMD_CHANNEL_KEEP_YES = 0x12
        internal const val MSG_CMD_CHANNEL_KEEP_NO = 0x13
        internal const val MSG_CMD_SERVENT_DISCONNECT = 0x20

        private const val TAG = "PeCaCtrl"
        private const val PKG_PEERCAST = "org.peercast.core"
        private const val CLASS_NAME_PEERCAST_SERVICE = "$PKG_PEERCAST.PeerCastService"

        fun from(c: Context) = PeerCastController(c.applicationContext)
    }
}


class Properties(private val b: Bundle) {
    /**ピアキャス動作ポート。停止時は0。*/
    val port: Int
        get() = b.getInt("port")
}

class Channel(private val b: Bundle) {

    val id: String
        get() = b.getString("id") ?: ""

    val channel_id: Int
        get() = b.getInt("channel_id")

    val totalListeners: Int
        get() = b.getInt("totalListeners")

    val totalRelays: Int
        get() = b.getInt("totalRelays")

    val localListeners: Int
        get() = b.getInt("localListeners")

    val localRelays: Int
        get() = b.getInt("localRelays")

    val status: Int
        get() = b.getInt("status")

    val isStayConnected: Boolean
        get() = b.getBoolean("stayConnected")

    val isTracker: Boolean
        get() = b.getBoolean("tracker")

    val lastSkipTime: Int
        get() = b.getInt("lastSkipTime")

    val skipCount: Int
        get() = b.getInt("skipCount")

    val info by lazy {
        ChannelInfo(b.getBundle("info")!!)
    }

    val servents by lazy {
        linkedBundleToList(b.getBundle("servent"), ::Servent)
    }

    override fun toString(): String =
        "Channel(id=$id,listeners=[$localListeners,$localRelays$totalListeners,$totalRelays],info=$info,servents=$servents)"

    companion object {
        const val S_NONE = 0
        const val S_WAIT = 1
        const val S_CONNECTING = 2
        const val S_REQUESTING = 3
        const val S_CLOSING = 4
        const val S_RECEIVING = 5
        const val S_BROADCASTING = 6
        const val S_ABORT = 7
        const val S_SEARCHING = 8
        const val S_NOHOSTS = 9
        const val S_IDLE = 10
        const val S_ERROR = 11
        const val S_NOTFOUND = 12

        const val T_NONE = 0
        const val T_ALLOCATED = 1
        const val T_BROADCAST = 2
        const val T_RELAY = 3
    }
}

/**
 * サーバントの状態
 * */
class Servent(b: Bundle) {
    val servent_id: Int = b.getInt("servent_id")
    val isRelay: Boolean = b.getBoolean("relay")
    val isFirewalled: Boolean = b.getBoolean("firewalled")
    val isSetInfoFlg: Boolean = b.getBoolean("infoFlg")
    val numRelays: Int = b.getInt("numRelays")
    val host: String = b.getString("host", "")
    val port: Int = b.getInt("port")
    val totalListeners: Int = b.getInt("totalListeners")
    val totalRelays: Int = b.getInt("totalRelays")
    val version: String = b.getString("version", "")
}

/**
 * チャンネル情報
 * */
class ChannelInfo(b: Bundle) {
    val id: String = b.getString("id") ?: ""
    val type: Int = b.getInt("contentType")
    val typeStr: String = b.getString("typeStr", "")
    val trackArtist: String = b.getString("track.artist", "")
    val trackTitle: String = b.getString("track.title", "")
    val name: String = b.getString("name", "")
    val desc: String = b.getString("desc", "")
    val genre: String = b.getString("genre", "")
    val comment: String = b.getString("comment", "")
    val url: String = b.getString("url", "")
    val bitrate: Int = b.getInt("bitrate")

    /**
     * チャンネル再生用のURL
     */
    fun getStreamUrl(port: Int = 7144): Uri {
        val u = when (val type = typeStr.toLowerCase()) {
            "wmv" -> "mmsh://localhost:$port/stream/$id.$type"
            else -> "http://localhost:$port/stream/$id.$type"
        }
        return Uri.parse(u)
    }

    /**再生用のインテントを生成する*/
    fun createIntent(port: Int = 7144): Intent {
        return Intent(Intent.ACTION_VIEW, getStreamUrl(port)).also {
            it.putExtra(EXTRA_NAME, name)
            it.putExtra(EXTRA_CONTACT_URL, url)
            it.putExtra(EXTRA_DESCRIPTION, desc)
            it.putExtra(EXTRA_COMMENT, comment)
        }
    }

    override fun toString(): String {
        return "ChannelInfo(id='$id', type=$type, typeStr='$typeStr', trackArtist='$trackArtist', trackTitle='$trackTitle', name='$name', desc='$desc', genre='$genre', comment='$comment', url='$url', bitrate=$bitrate)"
    }


    companion object {
        // enum ChanInfo::TYPE
        const val T_UNKNOWN = 0
        const val T_RAW = 1
        const val T_MP3 = 2
        const val T_OGG = 3
        const val T_OGM = 4
        const val T_MOV = 5
        const val T_MPG = 6
        const val T_NSV = 7
        const val T_WMA = 8
        const val T_WMV = 9
        const val T_PLS = 10
        const val T_ASX = 11

        /**チャンネルコンタクトURL (String)*/
        const val EXTRA_CONTACT_URL = "contact"

        /**チャンネル名 (String)*/
        const val EXTRA_NAME = "name"

        /**チャンネル詳細 (String)*/
        const val EXTRA_DESCRIPTION = "description"

        /**チャンネルコメント (String)*/
        const val EXTRA_COMMENT = "comment"

    }
}

/**
 * 通信量の状態
 * */
class Stats(b: Bundle) {
    val inBytes: Int = b.getInt("in_bytes")
    val outBytes: Int = b.getInt("out_bytes")
    val inTotalBytes: Long = b.getLong("in_total_bytes")
    val outTotalBytes: Long = b.getLong("out_total_bytes")

    override fun toString(): String {
        return "Stats(inBytes=$inBytes, outBytes=$outBytes, inTotalBytes=$inTotalBytes, outTotalBytes=$outTotalBytes)"
    }
}

/**
 *  ネイティブ側で作成したBundleはリンクリストのように次の要素がnextに収納されている。
 * */
private fun <T> linkedBundleToList(
    src: Bundle?, creator: (Bundle) -> T
): List<T> {
    // empty|null -> null
    fun Bundle?.emptyToNull(): Bundle? = if (this?.isEmpty == true) null else this

    return generateSequence(src.emptyToNull()) { cur ->
        cur.getBundle("next").also { cur.remove("next") }.emptyToNull()
    }.map(creator).toList()
}
