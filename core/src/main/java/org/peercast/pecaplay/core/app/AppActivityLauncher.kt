package org.peercast.pecaplay.core.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import org.peercast.pecaplay.core.R
import timber.log.Timber


fun launchPecaViewer(src: Activity, streamUri: Uri, ch: Yp4gChannel) {
    val it = Intent()
    it.data = streamUri
    it.component = PecaViewerIntent.COMPONENT_NAME
    it.putExtra(PecaViewerIntent.EX_YP4G_CHANNEL, ch)
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

    if (NavUtils.shouldUpRecreateTask(src, it)) {
        TaskStackBuilder.create(src).addNextIntentWithParentStack(it).startActivities()
    } else {
        src.navigateUpTo(it)
    }
}

fun backToPecaPlay(src: Activity) {
    val actMan = src.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    var isLaunchPlay = true
    //スタックにPecaPlayが残っていれば、フォアグラウンドに持ってくる
    for (t in actMan.appTasks) {
        if (PecaPlayIntent.COMPONENT_NAME == t.taskInfo.baseActivity) {
            Timber.i("PecaPlayActivity exists, so try moveToFront()")
            t.moveToFront()
            isLaunchPlay = false
            break
        }
    }

    if (isLaunchPlay) {
        //通知バーまたはPIPモードから復帰した場合、戻るPecaPlayがないので作成する
        Timber.d("Try to launch PecaPlayActivity.")
        launchPecaPlay(src)
    }

    src.finish()
    src.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
}

