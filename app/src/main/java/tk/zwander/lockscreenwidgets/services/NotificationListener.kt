package tk.zwander.lockscreenwidgets.services

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import tk.zwander.lockscreenwidgets.util.Event
import tk.zwander.lockscreenwidgets.util.eventManager
import tk.zwander.lockscreenwidgets.util.logUtils

/**
 * Used to notify the Accessibility service about changes in notification count,
 * along with the actual count. If the number of notifications with
 * [Notification.visibility] == [Notification.VISIBILITY_PUBLIC] or [Notification.VISIBILITY_PRIVATE]
 * AND [Notification.priority] > [Notification.PRIORITY_MIN] (importance for > Nougat) is greater than 0,
 * and the user has the option enabled, the widget frame will hide.
 */
class NotificationListener : NotificationListenerService() {
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
            //Even with the check to make sure the notification listener is connected, some devices still
            //crash with an "unknown listener" error when trying to retrieve the notification lists.
            //This shouldn't happen, but it does, so catch the Exception.
            try {
                eventManager.sendEvent(Event.NewNotificationCount(
                    (activeNotifications ?: arrayOf()).filter {
                        it.notification.visibility
                            .run { this == Notification.VISIBILITY_PUBLIC || this == Notification.VISIBILITY_PRIVATE }
                                && (
                                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                                    val ranking = Ranking().apply { currentRanking.getRanking(it.key, this) }
                                    ranking.importance > NotificationManager.IMPORTANCE_MIN
                                } else {
                                    @Suppress("DEPRECATION")
                                    it.notification.priority > Notification.PRIORITY_MIN
                                })
                    }.size
                ))
            } catch (e: Exception) {
                logUtils.debugLog("Error sending notification count update", e)
            } catch (e: OutOfMemoryError) {
                logUtils.debugLog("Error sending notification count update", e)
            }
        }
    }
}