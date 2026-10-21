package tk.zwander.common.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WidgetStackProvider.updateWidgetStackMonitor(context, true)
        }
    }
}
