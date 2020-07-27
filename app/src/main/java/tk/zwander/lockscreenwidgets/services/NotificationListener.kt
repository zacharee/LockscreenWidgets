package tk.zwander.lockscreenwidgets.services

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Used to notify the Accessibility service about changes in notification count,
 * along with the actual count. If the number of notifications with
 * [Notification.visibility] == [Notification.VISIBILITY_PUBLIC] or [Notification.VISIBILITY_PRIVATE]
 * AND [Notification.priority] > [Notification.PRIORITY_MIN] (importance for > Nougat) is greater than 0,
 * and the user has the option enabled, the widget frame will hide.
 */
class NotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_NEW_NOTIFICATION_COUNT = "NEW_NOTIFICATION_COUNT"
        const val EXTRA_NOTIFICATION_COUNT = "NOTIFICATION_COUNT"
    }

    /**
     * Convenience class for listening to changes in the notification count.
     */
    abstract class NotificationCountListener : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NEW_NOTIFICATION_COUNT) {
                onUpdate(intent.getIntExtra(EXTRA_NOTIFICATION_COUNT, 0))
            }
        }

        fun register(context: Context) {
            LocalBroadcastManager.getInstance(context)
                .registerReceiver(this, IntentFilter(ACTION_NEW_NOTIFICATION_COUNT))
        }

        fun unregister(context: Context) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
        }

        abstract fun onUpdate(count: Int)
    }

    private var isListening = false

    override fun onListenerConnected() {
        isListening = true
    }

    override fun onListenerDisconnected() {
        isListening = false
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
        if (isListening) {
            val intent = Intent(ACTION_NEW_NOTIFICATION_COUNT)

            intent.putExtra(EXTRA_NOTIFICATION_COUNT, (activeNotifications ?: arrayOf()).filter {
                it.notification.visibility
                    .run { this == Notification.VISIBILITY_PUBLIC || this == Notification.VISIBILITY_PRIVATE }
                        && (
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                            val ranking = Ranking().apply { currentRanking.getRanking(it.key, this) }
                            ranking.importance > NotificationManager.IMPORTANCE_MIN
                        } else {
                            it.notification.priority > Notification.PRIORITY_MIN
                        })
            }.size)

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }
}