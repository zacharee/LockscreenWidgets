package tk.zwander.common.appwidget

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import tk.zwander.lockscreenwidgets.R

class WidgetStackMonitorService : Service() {
    private val nm by lazy { NotificationManagerCompat.from(this) }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(
                "widget_stack_monitor",
                NotificationManagerCompat.IMPORTANCE_LOW,
            ).setName(resources.getString(R.string.widget_stack_monitor))
                .setDescription(resources.getString(R.string.widget_stack_monitor_desc))
                .build(),
        )
    }

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            100,
            NotificationCompat.Builder(this, "widget_stack_monitor")
                .setContentTitle(resources.getString(R.string.widget_stack_monitor))
                .setContentText(resources.getString(R.string.tap_to_hide))
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentIntent(
                    PendingIntentCompat.getActivity(
                        this,
                        500000,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                        0,
                        false,
                    ),
                )
                .build(),
        )

        return START_STICKY
    }
}
