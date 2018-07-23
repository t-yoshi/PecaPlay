package org.peercast.pecaplay

import android.app.Dialog
import android.arch.lifecycle.Observer
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import okhttp3.ResponseBody
import org.peercast.pecaplay.app.AppTheme
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.databinding.SpeedtestDialogFooterBinding
import org.peercast.pecaplay.yp4g.RandomDataBody
import org.peercast.pecaplay.yp4g.Yp4gConfig
import org.peercast.pecaplay.yp4g.Yp4gService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber


class SpeedTestFragment : AppCompatDialogFragment(),
        DialogInterface.OnShowListener {

    private val dialog get() = super.getDialog() as AlertDialog
    private var selected: YpTester? = null
    private var activeCall: Call<*>? = null
    private lateinit var adapter: ArrayAdapter<YpTester>
    private val ready = HashMap<YellowPage, Boolean>()
    private lateinit var binding: SpeedtestDialogFooterBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = ArrayAdapter(context, R.layout.appcompat_simple_list_item_single_choice)
        isCancelable = false
        binding = SpeedtestDialogFooterBinding.inflate(LayoutInflater.from(context))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val icon = context.getDrawable(R.drawable.ic_file_upload_36dp)
        icon.setTintList(AppTheme(context).iconTint)
        val checkedItem = savedInstanceState?.getInt(STATE_KEY_CHECKED, -1) ?: -1

        val dialog = AlertDialog.Builder(context, theme)
                .setSingleChoiceItems(adapter, checkedItem) { _, which ->
                    selected = adapter.getItem(which)
                    updateViews()
                }
                .setPositiveButton(R.string.start, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setTitle(R.string.speed_test)
                .setIcon(icon)
                .create()

        dialog.setOnShowListener(this)
        return dialog
    }

    override fun onShow(d: DialogInterface?) {
        dialog.listView.addFooterView(binding.root, null, false)

        val viewModel = PecaViewModel.get(activity!!)
        viewModel.database.getYellowPageDao().getEnabled().observe(this, Observer {
            adapter.clear()
            it?.forEach {
                adapter.add(YpTester(it, ::updateViews).apply {
                    loadConfig()
                })
            }
        })
    }

    override fun onCancel(dialog: DialogInterface?) {
        activeCall?.cancel()
    }

    private fun updateViews() {
        val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        selected?.let { tester ->
            binding.status = tester.status

            btn.isEnabled = activeCall == null &&
                    tester.config.uptest.isCheckable &&
                    ready[tester.yp] != false

            btn.setOnClickListener {
                btn.isEnabled = false
                tester.startTest({
                    binding.progress = it
                }, {
                    activeCall = it
                    ready[tester.yp] = false
                }, {
                    activeCall = null
                    launch {
                        delay(tester.config.uptest_srv.interval * 1000L)
                        ready[tester.yp] = true
                        //eventLiveData.postValue(Event.UpdateView)
                        if (this@SpeedTestFragment.isVisible)
                            activity.runOnUiThread { updateViews() }
                    }
                })
            }
        } ?: kotlin.run {
            btn.isEnabled = false
        }
        adapter.notifyDataSetChanged()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_KEY_CHECKED, adapter.getPosition(selected))
    }

    companion object {
        private const val TAG = "SpeedTestFragment"
        private const val STATE_KEY_CHECKED = "$TAG#checked"
    }
}


private class YpTester(val yp: YellowPage, private val onUpdate: () -> Unit) {
    var config = Yp4gConfig.NONE
        private set
    private var error = ""

    fun loadConfig() {
        val service = SquareUtils.retrofitBuilder()
                .baseUrl(yp.url)
                .addConverterFactory(SquareUtils.SIMPLE_XML_CONVERTER_FACTORY)
                .build()
                .create(Yp4gService::class.java)

        service.getConfig().enqueue(object : Callback<Yp4gConfig> {
            override fun onResponse(call: Call<Yp4gConfig>, response: Response<Yp4gConfig>) {
                Timber.i( "loadConfig OK: $config")
                config = response.body()!!
                onUpdate()
            }

            override fun onFailure(call: Call<Yp4gConfig>, t: Throwable) {
                Timber.e( t, "loadConfig Failed: $t")
                config = Yp4gConfig.NONE
                error = t.localizedMessage
                onUpdate()
            }
        })
    }

    fun startTest(onProgress: (Int) -> Unit,
                  onStart: (Call<*>) -> Unit,
                  onFinished: () -> Unit) {
        Timber.d( "startTest $config")

        val u = config.uptest_srv.run { "http://$addr:$port/" }
        val service = SquareUtils.retrofitBuilder()
                .baseUrl(u)
                .build()
                .create(Yp4gService::class.java)

        val reqBody = RandomDataBody(
                config.uptest_srv.postSize * 1024,
                config.uptest_srv.limit * 1024 / 8,
                onProgress
        )

        Timber.d( "config=$config post=$reqBody")

        val obj = config.uptest_srv.`object`
        service.speedTest(obj, reqBody).also {
            onStart(it)
            it.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    Log.i(TAG, "SpeedTest OK: " + response.body()?.string())
                    error = ""
                    onFinished()
                    loadConfig()
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.e(TAG, "$t", t)
                    error = t.localizedMessage
                    onUpdate()
                    onFinished()
                }
            })
        }
    }

    val status: String
        get() {
            if (error.isNotEmpty())
                return error

            var s = "${config.host.speed}kbps"
            if (config.host.isOver)
                s += " > "
            if (!config.host.isPortOpen) {
                s += " (closed port)"
            }
            return s
        }

    override fun toString() = yp.name

    companion object {
        private const val TAG = "YpTester"
    }
}

