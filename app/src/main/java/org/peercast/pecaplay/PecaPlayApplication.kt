package org.peercast.pecaplay

import android.app.Activity
import android.app.Application
import android.app.Service
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import org.peercast.core.PeerCastController
import org.peercast.pecaplay.app.AppNightMode
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.prefs.AppPreferences
import timber.log.Timber


class PecaPlayApplication : Application() {
    lateinit var database: AppRoomDatabase
        private set

    private var peerCastController: PeerCastController? = null


    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        database = AppRoomDatabase.create(this)

        AppNightMode.init(this)
        PlayerLauncherSettings(this).setEnabledFromPreference()

        val appPrefs = AppPreferences(this)

        if (appPrefs.peerCastUrl.host in listOf("localhost", "127.0.0.1")) {
            val controller = createPeerCastController { port ->
                val u = Uri.parse("http://localhost:$port/")
                appPrefs.peerCastUrl = u
                EVENT_BUS.postSticky(OnPeerCastStart(u.port))
            }
            if (controller.isInstalled)
                peerCastController = controller
        }
        if (peerCastController != null) {
            bindPeerCastService()
        } else {
            //外部で動作するPeerCast [192.168.0.XX:7144]
            EVENT_BUS.postSticky(OnPeerCastStart(-1))
        }
    }

    private fun createPeerCastController(onServiceStarted: (Int) -> Unit) : PeerCastController {
        return PeerCastController(this).also { controller ->
            controller.onEventListener = object : PeerCastController.OnEventListener {
                override fun onServiceConnected() {
                    controller.sendCommand(PeerCastController.Request.GET_APPLICATION_PROPERTIES)
                }

                override fun onServiceDisconnected() {
                }

                override fun onServiceResult(req: PeerCastController.Request, data: Bundle) {
                    when (req) {
                        PeerCastController.Request.GET_APPLICATION_PROPERTIES -> {
                            val port = data.getInt("port")
                            onServiceStarted(port)
                        }
                        else -> {
                            throw RuntimeException()
                        }
                    }
                }
            }
        }
    }

    /**
     * PeerCastがインストールされていればbindする。
     * 成功すれば [OnPeerCastStart]イベントをstickyで送出する。
     * */
    fun bindPeerCastService() {
        peerCastController?.let {
            if (!it.isConnected)
                it.bindService()
        }
    }


    companion object {
        fun of(s: Service) = s.application as PecaPlayApplication
        fun of(a: Activity) = a.application as PecaPlayApplication
        fun of(f: Fragment) = of(f.activity!!)
    }
}