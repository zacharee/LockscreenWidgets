package tk.zwander.lockscreenwidgets.services

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_NEW_NOTIFICATION_COUNT = "NEW_NOTIFICATION_COUNT"
        const val EXTRA_NOTIFICATION_COUNT = "NOTIFICATION_COUNT"
    }

    abstract class NotificationCountListener : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NEW_NOTIFICATION_COUNT) {
                onUpdate(intent.getIntExtra(EXTRA_NOTIFICATION_COUNT, 0))
            }
        }

        fun register(context: Context) {
            LocalBroadcastManager.getInstance(context).registerReceiver(this, IntentFilter(ACTION_NEW_NOTIFICATION_COUNT))
        }

        fun unregister(context: Context) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
        }

        abstract fun onUpdate(count: Int)
    }

    override fun onCreate() {
        sendUpdate()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sendUpdate()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sendUpdate()
    }

    private fun sendUpdate() {
        val intent = Intent(ACTION_NEW_NOTIFICATION_COUNT)
        intent.putExtra(EXTRA_NOTIFICATION_COUNT, activeNotifications.filter { it.notification.priority > Notification.PRIORITY_MIN }.size)

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}