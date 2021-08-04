package org.peercast.pecaplay.core.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import org.peercast.pecaplay.core.R
import timber.log.Timber

class AppActivityLauncher(val a: Application) {
    private val stackedActivities = ArrayList<String>()

    private val callback = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            //stackedActivities.add(activity.componentName.className)
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
            stackedActivities.remove(activity.componentName.className)
        }
    }

    init {
        a.registerActivityLifecycleCallbacks(callback)
    }

    fun launchPecaViewer(src: Activity, streamUri: Uri, ch: Yp4gChannel) {
        val it = Intent()
        it.data = streamUri
        it.component = PecaViewerIntent.COMPONENT_NAME
        it.putExtra(PecaViewerIntent.EX_YP4G_CHANNEL, ch)
        it.putExtra(PecaViewerIntent.EX_LAUNCHED_FROM, javaClass.canonicalName)
        //it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        //it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        src.startActivity(it)
        src.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    fun launchPecaPlay(src: Activity) {
        val it = Intent()
        it.action = Intent.ACTION_MAIN
        //CATEGORY_LAUNCHERは付けずに、
        // システムランチャーからの起動とは区別する
        it.component = PecaPlayIntent.COMPONENT_NAME
        //it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        //it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        //it.putExtra(PecaPlayIntent.EX_LAUNCHED_FROM, javaClass.canonicalName)

        if (NavUtils.shouldUpRecreateTask(src, it)) {
            TaskStackBuilder.create(src).addNextIntentWithParentStack(it).startActivities()
        } else {
            src.navigateUpTo(it)
        }
    }

    fun backToPecaPlay(src: Activity) {
        if (PecaPlayIntent.COMPONENT_NAME.className !in stackedActivities) {
            //通知バーまたはPIPモードから復帰した場合、戻るPecaPlayがない
            Timber.d(" launch ParentActivity.")
            launchPecaPlay(src)
        } else {
            //スタックにPecaPlayが残っていれば、単にfinishすれば戻れる
            Timber.d(" it is just finish.")
        }
        src.finish()
        src.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }


}