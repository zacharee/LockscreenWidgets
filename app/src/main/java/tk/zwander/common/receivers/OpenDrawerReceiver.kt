package tk.zwander.common.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.lockscreenwidgets.BuildConfig

class OpenDrawerReceiver : BroadcastReceiver() {
    companion object {
        const val OPEN_ACTION = "${BuildConfig.APPLICATION_ID}.action.OPEN_DRAWER"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == OPEN_ACTION) {
            context.eventManager.sendEvent(Event.ShowDrawer)
        }
    }
}