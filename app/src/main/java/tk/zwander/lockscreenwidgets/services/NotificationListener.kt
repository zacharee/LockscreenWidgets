package tk.zwander.lockscreenwidgets.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.SystemProperties
import android.provider.Settings
import android.service.notification.INotificationListener
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bugsnag.android.Bugsnag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
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
@Suppress("unused")
class NotificationListener : NotificationListenerService(), EventObserver, CoroutineScope by MainScope() {
    private var isListening = false

    override fun onListenerConnected() {
        isListening = true
        eventManager.addObserver(this)
        sendUpdate()
    }

    override fun onListenerDisconnected() {
        isListening = false
        eventManager.removeObserver(this)
    }

    override fun onCreate() {
        sendUpdate()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mWrapper = NougatListenerWrapper()
        } else {
            try {
                val wrapperClass = Class.forName("android.service.notification.NotificationListenerService\$INotificationListenerWrapper")
                NotificationListenerService::class.java.getDeclaredField("mWrapper")
                    .apply {
                        isAccessible = true
                        val original = get(this@NotificationListener)

                        set(
                            this@NotificationListener,
                            ByteBuddy()
                                .subclass(wrapperClass)
                                .name("android.service.notification.WrappingNotificationListener")
                                .defineMethod("onTransact", Boolean::class.java)
                                .withParameters(Int::class.java, Parcel::class.java, Parcel::class.java, Int::class.java)
                                .intercept(MethodDelegation.to(LollipopListenerWrapper(original as INotificationListener.Stub)))
                                .make()
                                .load(NotificationListenerService::class.java.classLoader, AndroidClassLoadingStrategy.Wrapping(context.cacheDir))
                                .loaded
                                .getDeclaredConstructor()
                                .apply { isAccessible = true }
                                .newInstance()
                        )
                    }
            } catch (e: Throwable) {
                Bugsnag.notify(IllegalStateException("Error creating Lollipop notification listener", e))
            }
        }

        return NotificationListenerService::class.java.getDeclaredField("mWrapper")
            .apply { isAccessible = true }
            .get(this) as IBinder
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
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
        launch(Dispatchers.IO) {
            if (isListening) {
                //Even with the check to make sure the notification listener is connected, some devices still
                //crash with an "unknown listener" error when trying to retrieve the notification lists.
                //This shouldn't happen, but it does, so catch the Exception.
                try {
                    // This seems to cause ANRs on a bunch of devices, so run on a background thread.
                    val activeNotifications = activeNotifications
                    logUtils.debugLog("Filtering notifications ${activeNotifications.size}", null)
                    eventManager.sendEvent(
                        Event.NewNotificationCount(activeNotifications?.count { it.shouldCount } ?: 0)
                    )
                } catch (e: Exception) {
                    logUtils.normalLog("Error sending notification count update", e)
                    Bugsnag.notify(e)
                } catch (e: OutOfMemoryError) {
                    logUtils.normalLog("Error sending notification count update", e)
                }
            }
        }
    }

    private val StatusBarNotification.shouldCount: Boolean
        get() {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (notification.flags and Notification.FLAG_BUBBLE != 0 &&
                    notification.bubbleMetadata.isNotificationSuppressed
                ) {
                    logUtils.debugLog("Bubble and suppressed ${this.channelIdLogTag}", null)
                    return false
                }
            }

            if (notification.visibility == Notification.VISIBILITY_SECRET) {
                logUtils.debugLog("Secret visibility ${this.channelIdLogTag}", null)
                return false
            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                val ranking = Ranking()
                val rankingResult = currentRanking.getRanking(key, ranking)

                if (rankingResult && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ranking.lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET
                ) {
                    logUtils.debugLog("Secret ranking ${this.channelIdLogTag}", null)
                    return false
                }

                val importance =
                    if (!rankingResult || ranking.importance == NotificationManager.IMPORTANCE_NONE) {
                        nm.getNotificationChannel(notification.channelId).importance
                    } else {
                        ranking.importance
                    }

                if (importance <= NotificationManager.IMPORTANCE_MIN) {
                    logUtils.debugLog("Min important ${this.channelIdLogTag}", null)
                    return false
                }

                if (importance <= NotificationManager.IMPORTANCE_LOW &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    nm.shouldHideSilentStatusBarIcons()
                ) {
                    logUtils.debugLog("Low importance and silent hidden ${this.channelIdLogTag}", null)
                    return false
                }

                // There's a bug in Pixel UI 13 where silent notifications don't
                // show on the lock screen even if they're set to do so.
                if (importance <= NotificationManager.IMPORTANCE_LOW &&
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU &&
                    SystemProperties.get("ro.vendor.camera.extensions.service")
                        .contains("com.google.android.apps.camera.services.extensions.service.PixelExtensions")
                ) {
                    logUtils.debugLog("Pixel workaround ${this.channelIdLogTag}", null)
                    return false
                }
            } else {
                @Suppress("DEPRECATION")
                if (notification.priority <= Notification.PRIORITY_MIN) {
                    logUtils.debugLog("Priority min ${this.packageName}", null)
                    return false
                }
            }

            return true
        }

    private inner class NougatListenerWrapper : NotificationListenerService.NotificationListenerWrapper() {
        override fun onTransact(code: Int, data: Parcel?, reply: Parcel?, flags: Int): Boolean {
            return try {
                super.onTransact(code, data, reply, flags)
            } catch (e: Throwable) {
                false
            }
        }
    }

    private inner class LollipopListenerWrapper(private val wrapper: INotificationListener.Stub) {
        fun onTransact(code: Int, data: Parcel?, reply: Parcel?, flags: Int): Boolean {
            return try {
                wrapper.onTransact(code, data, reply, flags)
            } catch (e: Throwable) {
                false
            }
        }
    }
}