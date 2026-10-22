package tk.zwander.common.appwidget

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.*
import androidx.core.graphics.drawable.IconCompat
import tk.zwander.common.util.getAttrColor
import tk.zwander.common.util.logUtils
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider


class WidgetStackMonitorService : Service() {
    private val nm by lazy { NotificationManagerCompat.from(this) }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        logUtils.debugLog("WidgetStackMonitorService created", null)
        super.onCreate()

        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(
                NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW,
            ).setName(resources.getString(R.string.widget_stack_monitor))
                .setDescription(resources.getString(R.string.widget_stack_monitor_desc))
                .build(),
        )

        // Go foreground as early as possible, so a pending startForegroundService() is satisfied
        // even if onStartCommand() is delayed or never delivered.
        startForegroundWithNotification()

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logUtils.debugLog("WidgetStackMonitorService started", null)

        // Every startForegroundService() call must be followed by startForeground(), even if
        // we're about to stop.
        startForegroundWithNotification()

        if (!WidgetStackProvider.shouldRunWidgetStackMonitor(this)) {
            logUtils.debugLog("WidgetStackMonitorService no longer needed, stopping", null)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundWithNotification() {
        val color = getAttrColor(android.R.attr.textColorPrimary)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(resources.getString(R.string.widget_stack_monitor))
            .setContentText(resources.getString(R.string.tap_to_hide))
            .setSmallIcon(R.drawable.app_icon)
            .setSmallIcon(
                IconCompat
                    .createWithResource(this, R.drawable.app_icon)
                    .setTint(color),
            )
            .setContentIntent(
                PendingIntentCompat.getActivity(
                    this,
                    500001,
                    Intent(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                        } else {
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        },
                    ).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra("app_package", packageName)
                        .putExtra("app_uid", applicationInfo.uid)
                        .putExtra("package", packageName)
                        .putExtra("uid", applicationInfo.uid)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    0,
                    false,
                ),
            )
            .build()

        ServiceCompat.startForeground(
            this,
            100,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    override fun onDestroy() {
        logUtils.debugLog("WidgetStackMonitorService destroyed", null)
        super.onDestroy()

        isRunning = false
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "widget_stack_monitor"

        var isRunning = false
            private set
    }
}
