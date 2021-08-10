package org.peercast.pecaviewer.chat

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.chat.net.IBoardThreadPoster
import org.peercast.pecaviewer.chat.net.PostMessage
import org.peercast.pecaviewer.databinding.PostMessageDialogFragmentBinding

class PostMessageDialogFragment : BottomSheetDialogFragment(),
    DialogInterface.OnShowListener {
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private lateinit var binding: PostMessageDialogFragmentBinding
    private lateinit var poster: IBoardThreadPoster

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        poster = chatViewModel.selectedThreadPoster.value ?: return dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = PostMessageDialogFragmentBinding.inflate(layoutInflater, null, false)
        return super.onCreateDialog(savedInstanceState).also { d ->
            d.setContentView(binding.root)
            d.setOnShowListener(this)
        }
    }

    override fun onShow(dialog: DialogInterface) {
        val u = poster.info.url
        dialog as BottomSheetDialog

        chatViewModel.messageDraft[u]?.let(binding.vEdit::setText)
        val sendClickListener = View.OnClickListener {
            binding.vEdit.isEnabled = false
            binding.vSend.isEnabled = false
            chatViewModel.presenter.postMessage(
                poster,
                PostMessage("", "sage", binding.vEdit.text.toString())
            )
            chatViewModel.messageDraft.remove(u)
            dismiss()
        }

        binding.vSend.setOnClickListener(sendClickListener)
        binding.vEdit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND && binding.vSend.isEnabled) {
                sendClickListener.onClick(v)
                true
            } else {
                false
            }
        }
        binding.vEdit.hint = "${poster.info.title} (${poster.info.numMessages})"
        binding.vEdit.requestFocus()

        binding.vEdit.doOnTextChanged { text, _, _, _ ->
            chatViewModel.messageDraft[u] = text?.toString() ?: ""
            binding.vSend.isEnabled = text?.isNotEmpty() == true
        }
    }

}