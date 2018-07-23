package org.peercast.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import timber.log.Timber

class PeerCastController(private val context: Context) {
    private var serverMessenger: Messenger? = null

    interface OnEventListener {
        /**PeerCastServiceに接続した*/
        fun onServiceConnected()

        /**PeerCastServiceがKillされた*/
        fun onServiceDisconnected()

        /**PeerCastServiceへのリクエストに対する戻り値*/
        fun onServiceResult(req: Request, data: Bundle)
    }

    enum class Request(val msgId: Int) {
        GET_APPLICATION_PROPERTIES(0x00),
        GET_CHANNELS(0x01),
        GET_STATS(0x02),
        CHANNEL_BUMP(0x10),
        CHANNEL_DISCONNECT(0x11),
        CHANNEL_KEEP_YES(0x12),
        CHANNEL_KEEP_NO(0x13),
        SERVENT_DISCONNECT(0x20);

        companion object {
            fun get(msgId: Int) = values().firstOrNull { it.msgId == msgId }
                    ?: throw IllegalArgumentException("msgId=%x not found")
        }
    }

    var onEventListener : OnEventListener? =null

    private val clientMessenger = Messenger(Handler(Handler.Callback { msg ->
        Request.values().find { msg.what == it.msgId }?.let {
            onEventListener?.onServiceResult(it, msg.data)
            true
        } ?: false
    }))


    val isConnected
        get() = serverMessenger != null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, binder: IBinder) {
            serverMessenger = Messenger(binder)
            onEventListener?.onServiceConnected()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            // OSにKillされたとき。
            serverMessenger = null
            onEventListener?.onServiceDisconnected()
        }
    }

    val isInstalled: Boolean
        get() {
            return try {
                context.packageManager.getApplicationInfo(PKG_PEERCAST, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

    private fun checkServiceConnected(): Messenger {
        serverMessenger.let {
            if (it == null)
                throw IllegalStateException("service not connected.")
            return it
        }
    }

    /**
     * PeerCastServiceにコマンドを送り、戻り値を得る。
     *
     * GET_APPLICATION_PROPERTIES<br></br>
     * ピアキャスの動作ポートを取得します。<br></br>
     * 戻り値: getInt("port") ピアキャス動作ポート。停止時は0。<br></br>
     * <br></br>
     *  *
     * GET_CHANNELS;<br></br>
     * 現在アクティブなチャンネルの情報を取得します。<br></br>
     * 戻り値: nativeGetChannel()参照。<br></br>
     * ラッパー: Channel.java <br></br>
     * <br></br>
     *  *
     * GET_STATS<br></br>
     * 通信量の状況を取得します。<br></br>
     * 戻り値: nativeGetStats()参照。<br></br>
     * ラッパー: Stats.java <br></br>
     * <br></br>
     *
     * @see eventLiveData
     * @see Request
     * サービスからの戻り値をBundleで受け取る。
     */
    fun sendCommand(req: Request) {
        val messenger = checkServiceConnected()
        try {
            val msg = Message.obtain(null, req.msgId)
            msg.replyTo = clientMessenger
            messenger.send(msg)
        } catch (e: RemoteException) {
            Timber.e(e, "msgId=%d", req.msgId)
        }
    }

    /**
     * チャンネルに関する操作を行う。
     *
     * CHANNEL_BUMP;<br></br>
     * bumpして再接続する。 <br></br>
     * <br></br>
     *  *
     * CHANNEL_DISCONNECT<br></br>
     * チャンネルを切断する。 <br></br>
     * <br></br>
     *  *
     * CHANNEL_KEEP_YES<br></br>
     * チャンネルをキープする。<br></br>
     * <br></br>
     *  *
     * CHANNEL_KEEP_NO<br></br>
     * チャンネルのキープを解除する。<br></br>
     * <br></br>
     *
     * @param channel_id
     * 対象のchannel_id
     */
    private fun sendChannelCommand(req: Request, channel_id: Int) {
        checkServiceConnected()
        try {
            val msg = Message.obtain(null, req.msgId, channel_id, 0)
            serverMessenger!!.send(msg)
        } catch (e: RemoteException) {
            Timber.e(e, "msgId=%d", req.msgId)
        }
    }

    /**
     * Bumpして再接続する。
     *
     * @param channel_id
     * 対象のchannel_id
     */
    fun bumpChannel(channel_id: Int) {
        sendChannelCommand(Request.CHANNEL_BUMP, channel_id)
    }

    /**
     * チャンネルを切断する。
     *
     * @param channel_id
     * 対象のchannel_id
     */
    fun disconnectChannel(channel_id: Int) {
        sendChannelCommand(Request.CHANNEL_DISCONNECT, channel_id)
    }

    /**
     * チャンネルのキープと解除を設定する。
     *
     * @param channel_id
     * 対象のchannel_id
     * @param value
     */
    fun setChannelKeep(channel_id: Int, value: Boolean) {
        if (value)
            sendChannelCommand(Request.CHANNEL_KEEP_YES, channel_id)
        else
            sendChannelCommand(Request.CHANNEL_KEEP_NO, channel_id)
    }

    /**
     * 指定したServentを切断する。
     */
    fun disconnetServent(servent_id: Int) {
        checkServiceConnected()
        val msg = Message.obtain(null, Request.SERVENT_DISCONNECT.msgId,
                servent_id, 0)
        try {
            serverMessenger!!.send(msg)
        } catch (e: RemoteException) {
            Timber.e(e, "SERVENT_DISCONNECT")
        }

    }

    /**
     * context.bindServiceを呼び、PeerCastのサービスを開始する。
     * @return
     */
    fun bindService(): Boolean {
        if (!isInstalled) {
            Timber.e( "PeerCast not installed.")
            return false
        }
        val intent = Intent(CLASS_NAME_PEERCAST_SERVICE)
        // NOTE: LOLLIPOPからsetPackage()必須
        intent.`package` = PKG_PEERCAST
        return context.bindService(intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE)
    }


    /**
     * context.unbindServiceを呼ぶ。 他からもbindされていなければPeerCastサービスは終了する。
     */
    fun unbindService() {
        if (serverMessenger != null) {
            context.unbindService(serviceConnection)
            serverMessenger = null
        }
    }

    companion object {
        private const val TAG = "PeCaCtrl"
        private const val PKG_PEERCAST = "org.peercast.core"
        private const val CLASS_NAME_PEERCAST_SERVICE = PKG_PEERCAST + ".PeerCastService"
    }
}




