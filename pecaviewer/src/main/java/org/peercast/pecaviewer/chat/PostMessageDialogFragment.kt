package org.peercast.pecaviewer.chat

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.net.IBoardThreadPoster
import org.peercast.pecaviewer.chat.net.PostMessage

class PostMessageDialogFragment : BottomSheetDialogFragment(),
    DialogInterface.OnShowListener {
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private lateinit var poster: IBoardThreadPoster

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        poster = chatViewModel.selectedThreadPoster.value ?: return dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also { d ->
            d.setContentView(R.layout.fragment_post_message_dialog)
            d.setOnShowListener(this)
        }
    }

    override fun onShow(dialog: DialogInterface) {
        val u = poster.info.url

        with(dialog as BottomSheetDialog) {
            val vEdit = checkNotNull(findViewById<EditText>(R.id.vEdit))
            val vSend = checkNotNull(findViewById<Button>(R.id.vSend))

            chatViewModel.messageDraft[u]?.let(vEdit::setText)
            val sendClickListener = View.OnClickListener {
                vEdit.isEnabled = false
                vSend.isEnabled = false
                chatViewModel.presenter.postMessage(
                    poster,
                    PostMessage("", "sage", vEdit.text.toString())
                )
                chatViewModel.messageDraft.remove(u)
                dismiss()
            }

            vSend.setOnClickListener(sendClickListener)
            vEdit.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND && vSend.isEnabled) {
                    sendClickListener.onClick(v)
                    true
                } else {
                    false
                }
            }
            vEdit.hint = "${poster.info.title} (${poster.info.numMessages})"
            vEdit.requestFocus()

            vEdit.doOnTextChanged { text, _, _, _ ->
                chatViewModel.messageDraft[u] = text?.toString() ?: ""
                vSend.isEnabled = text?.isNotEmpty() == true
            }
        }
    }

}