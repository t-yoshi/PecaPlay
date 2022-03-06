package org.peercast.pecaplay.prefs

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.peercast.core.lib.PeerCastController
import org.peercast.pecaplay.R
import org.peercast.pecaplay.core.io.isLoopbackAddress
import org.peercast.pecaplay.core.io.isSiteLocalAddress
import org.peercast.pecaplay.databinding.PeercastServerDialogFragmentBinding

class PeerCastServerDialogFragment : DialogFragment(),
    DialogInterface.OnClickListener, DialogInterface.OnShowListener {

    private val appPrefs by inject<AppPreferences>()

    private val controller by lazy { PeerCastController.from(requireContext()) }
    private val bvm by lazy { BindingViewModel() }

    inner class BindingViewModel {
        val host: MutableStateFlow<CharSequence>
        val port: MutableStateFlow<CharSequence>

        init {
            appPrefs.peerCastUrl.let { u ->
                host = MutableStateFlow(u.host ?: "localhost")
                port = MutableStateFlow(u.port.toString())
            }
        }

        val isLocal = MutableStateFlow(
            controller.isInstalled && isLoopbackAddress(host.value.toString())
        )

        fun toUri() = Uri.parse("http://${host.value}:${port.value}/")!!

        val isValidUrl = combine(isLocal, host, port) { l, h, p ->
            //Timber.d("==> $l $h $p")
            toUri().run {
                //Issue #1
                isSiteLocalAddress() || isLoopbackAddress() ||
                        (host?.isNotBlank() == true && h == host)
            } && p.toString().toIntOrNull() in 1025..65532
        }.stateIn(lifecycleScope, SharingStarted.Lazily, false)

        fun onLocalServerChecked(b: Boolean) {
            isLocal.value = b
            if (b) {
                if (controller.isConnected) {
                    kotlin.runCatching {
                        Uri.parse(controller.rpcEndPoint)
                    }.onSuccess {
                        host.value = "localhost"
                        port.value = it.port.toString()
                    }
                }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (controller.isInstalled)
            controller.bindService()

        showsDialog = true
        isCancelable = false
    }

    private fun onCreateDialogView(): View {
        return PeercastServerDialogFragmentBinding.inflate(
            layoutInflater
        ).let { b ->
            b.isInstalled = controller.isInstalled
            b.vm = bvm
            b.lifecycleOwner = this
            b.root
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setView(onCreateDialogView())
            .setPositiveButton(android.R.string.ok, this)
            .setNegativeButton(android.R.string.cancel, this)
            .setTitle(R.string.pref_peercast_url)
            .create().also {
                it.setOnShowListener(this)
            }
    }

    override fun onShow(dialog: DialogInterface) {
        dialog as AlertDialog
        lifecycleScope.launch {
            bvm.isValidUrl.collect {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = it
            }
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        val result: Int
        if (which == DialogInterface.BUTTON_POSITIVE) {
            appPrefs.peerCastUrl = bvm.toUri()
            result = Activity.RESULT_OK
        } else {
            result = Activity.RESULT_CANCELED
        }
        setFragmentResult("", bundleOf("result" to result))
        dismiss()
    }

}