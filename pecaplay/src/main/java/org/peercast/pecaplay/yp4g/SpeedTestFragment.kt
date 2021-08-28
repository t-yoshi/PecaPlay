package org.peercast.pecaplay.yp4g

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.peercast.pecaplay.R
import org.peercast.pecaplay.app.AppRoomDatabase
import org.peercast.pecaplay.core.io.Square
import org.peercast.pecaplay.databinding.SpeedtestDialogFooterBinding
import org.peercast.pecaplay.util.AppTheme


class SpeedTestFragment : AppCompatDialogFragment() {

    private val database by inject<AppRoomDatabase>()
    private val square by inject<Square>()
    private lateinit var adapter: ArrayAdapter<Yp4gSpeedTester>
    private val viewModel = ViewModel()
    private val presenter = Presenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCancelable = false

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_single_choice)
        lifecycleScope.launch {
            database.yellowPageDao.query()
                .first()
                .map { Yp4gSpeedTester(requireContext(), square.okHttpClient, it) }
                .let(adapter::addAll)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = requireContext()
        val icon = ResourcesCompat.getDrawable(c.resources, R.drawable.ic_file_upload_36dp, c.theme)
        icon?.setTint(AppTheme.getIconColor(c))

        val binding = SpeedtestDialogFooterBinding.inflate(LayoutInflater.from(c))

        return MaterialAlertDialogBuilder(c, theme)
            .setSingleChoiceItems(adapter, -1) { _, which ->
                presenter.setTester(adapter.getItem(which)!!)
            }
            .setPositiveButton(R.string.start, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setTitle(R.string.speed_test)
            .setIcon(icon)
            .create().also { d ->
                d.setOnShowListener {
                    d.listView.addFooterView(binding.root, null, false)
                    d.startButton?.setOnClickListener {
                        presenter.startTest()
                    }
                    binding.viewModel = viewModel
                    binding.lifecycleOwner = this

                    lifecycleScope.launchWhenStarted {
                        viewModel.isStartButtonEnabled.collect {
                            d.startButton?.isEnabled = it
                        }
                    }
                }
            }
    }

    class ViewModel {
        val status = MutableStateFlow<CharSequence>("")
        val progress = MutableStateFlow(0)
        val isStartButtonEnabled = MutableStateFlow(false)
    }

    private inner class Presenter {
        private lateinit var tester: Yp4gSpeedTester

        fun setTester(t: Yp4gSpeedTester) {
            viewModel.status.value = ""
            tester = t
            lifecycleScope.launchWhenCreated {
                viewModel.isStartButtonEnabled.value =
                    tester.loadConfig() && tester.config.uptest.isCheckable
                viewModel.status.value = tester.status
            }
        }

        fun startTest() {
            viewModel.isStartButtonEnabled.value = false
            lifecycleScope.launchWhenResumed {
                tester.startTest {
                    //Timber.d("progress=$progress")
                    viewModel.progress.value = it
                }
                viewModel.status.value = tester.status
            }
        }
    }


    companion object {
        private const val TAG = "SpeedTestFragment"

        private val DialogInterface.startButton: Button?
            get() = (this as AlertDialog?)?.getButton(AlertDialog.BUTTON_POSITIVE)
    }


}


