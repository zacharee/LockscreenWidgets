package tk.zwander.lockscreenwidgets.services

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.logUtils

//Check if the notification listener service is enabled
val Context.isNotificationListenerActive: Boolean
    get() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.run {
        val cmp = ComponentName(this@isNotificationListenerActive, NotificationListener::class.java)
        contains(cmp.flattenToString()) || contains(cmp.flattenToShortString())
    } ?: false

/**
 * Used to notify the Accessibility service about changes in notification count,
 * along with the actual count. If the number of notifications with
 * [Notification.visibility] == [Notification.VISIBILITY_PUBLIC] or [Notification.VISIBILITY_PRIVATE]
 * AND [Notification.priority] > [Notification.PRIORITY_MIN] (importance for > Nougat),
 * and the user has the option enabled, the widget frame will hide.
 */
class NotificationListener : NotificationListenerService(), EventObserver {
    private var isListening = false

    override fun onListenerConnected() {
        isListening = true
        eventManager.addObserver(this)
    }

    override fun onListenerDisconnected() {
        isListening = false
        eventManager.removeObserver(this)
    }

    override fun onCreate() {
        sendUpdate()
    }

    override fun onEvent(event: Event) {
        when (event) {
            Event.RequestNotificationCount -> {
                sendUpdate()
            }
            else -> {}
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sendUpdate()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sendUpdate()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        sendUpdate()
    }

    private fun sendUpdate() {
        if (isListening) {
            //Even with the check to make sure the notification listener is connected, some devices still
            //crash with an "unknown listener" error when trying to retrieve the notification lists.
            //This shouldn't happen, but it does, so catch the Exception.
            try {
                eventManager.sendEvent(
                    Event.NewNotificationCount(activeNotifications?.count { it.shouldCount } ?: 0)
                )
            } catch (e: Exception) {
                logUtils.debugLog("Error sending notification count update", e)
            } catch (e: OutOfMemoryError) {
                logUtils.debugLog("Error sending notification count update", e)
            }
        }
    }

    private val StatusBarNotification.shouldCount: Boolean
        get() {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notification.visibility == Notification.VISIBILITY_SECRET) return false

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                val ranking = Ranking()
                val rankingResult = currentRanking.getRanking(key, ranking)

                if (rankingResult && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ranking.lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET) return false

                val importance = if (!rankingResult || ranking.importance == NotificationManager.IMPORTANCE_NONE) {
                    nm.getNotificationChannel(notification.channelId).importance
                } else {
                    ranking.importance
                }

                if (importance <= NotificationManager.IMPORTANCE_MIN) return false

                if (importance <= NotificationManager.IMPORTANCE_LOW &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    nm.shouldHideSilentStatusBarIcons()) return false
            } else {
                @Suppress("DEPRECATION")
                if (notification.priority <= Notification.PRIORITY_MIN) return false
            }

            return true
        }
}