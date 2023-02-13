package org.peercast.pecaviewer.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerServiceBinder(owner: LifecycleOwner) {
    private val _service = MutableStateFlow<PlayerService?>(null)
    val service: StateFlow<PlayerService?> get() = _service

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            _service.value = (service as PlayerService.Binder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    private val observer = object : DefaultLifecycleObserver {
        private fun LifecycleOwner.asContext(): Context {
            return when (this) {
                is AppCompatActivity -> this
                is Fragment -> requireContext()
                else -> throw RuntimeException()
            }
        }

        override fun onStart(owner: LifecycleOwner) {
            val c = owner.asContext()
            c.bindService(
                Intent(c, PlayerService::class.java),
                conn, Context.BIND_AUTO_CREATE
            )
        }

        override fun onStop(owner: LifecycleOwner) {
            owner.asContext().unbindService(conn)
            conn.onServiceDisconnected(null)
        }
    }

    init {
        check(owner is AppCompatActivity || owner is Fragment)
        owner.lifecycle.addObserver(observer)
    }

}