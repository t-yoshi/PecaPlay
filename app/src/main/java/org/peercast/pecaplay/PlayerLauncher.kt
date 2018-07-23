package org.peercast.pecaplay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.TaskStackBuilder
import android.widget.Toast
import org.peercast.pecaplay.prefs.AppPreferences
import timber.log.Timber

/**

 */
class PlayerLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val u = i.data
        if (i.action != Intent.ACTION_VIEW ||
                u == null || !u.scheme.matches("^(http|mmsh)".toRegex())) {
            finish()
            return
        }

        Timber.d("handlePlayerIntent($i)")
        try {
            if (intent.getBooleanExtra(EXTRA_IS_LAUNCH_FROM_PECAPLAY, false)) {
                startActivity(createPlayerIntent(intent.data))
            } else {
                //通知バーから視聴を再開した場合に戻るボタンでPecaPlayに帰って来れるようにする
                val taskStack = TaskStackBuilder.create(this)
                        .addNextIntent(createPecaPlayIntent())
                        .addNextIntent(createPlayerIntent(intent.data))
                taskStack.startActivities()
            }
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "startActivities failed.")
            Toast.makeText(this, "PecaPlayViewer not installed: \n$e", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun createPecaPlayIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).also {
            it.setClass(this, PecaPlayActivity::class.java)
        }
    }

    private fun createPlayerIntent(u: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, u).apply {
            setClassName(PECAVIEWER_PACKAGE, PECAVIEWER_MAIN_CLASS)
            //flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (intent.extras != null)
                putExtras(intent)
        }
    }
}

private const val PECAVIEWER_PACKAGE = "org.peercast.pecaviewer"
private const val PECAVIEWER_MAIN_CLASS = "$PECAVIEWER_PACKAGE.MainActivity"

class PlayerLauncherSettings(private val c: Context) {

    fun setEnabledType(type: String, enabled: Boolean) {
        val className = "${c.packageName}.PlayerLauncherActivity_${type.toUpperCase()}"
        val name = ComponentName(c.packageName, className)
        val state = when (enabled) {
            true -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            false -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        Timber.i("Update ComponentEnabledSetting($className, isSchedulable=$enabled")

        c.packageManager.setComponentEnabledSetting(
                name, state, PackageManager.DONT_KILL_APP
        )
    }

    fun setEnabledFromPreference() {
        val prefs = AppPreferences(c)
        val installed = isInstalledPlayer
        for (type in VIDEO_TYPES) {
            val enabled = prefs.isViewerEnabled(type)
            setEnabledType(type, installed && enabled)
        }
    }

    val isInstalledPlayer = installedPlayerVersion != null

    val installedPlayerVersion: String?
        get() {
            try {
                val info = c.packageManager.getPackageInfo(PECAVIEWER_PACKAGE, 0)
                return info.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }
        }


    companion object {
        private const val TAG = "PlayerLauncherActivity"

        val VIDEO_TYPES = listOf("WMV", "FLV")

    }
}

