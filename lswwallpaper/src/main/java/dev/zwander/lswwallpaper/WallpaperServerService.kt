package dev.zwander.lswwallpaper

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

class WallpaperServerService : Service() {
    companion object {
        private const val CHANNEL_ID = "service_notif"
    }

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()

        logUtils.debugLog("Wallpaper server service onCreate", null)

        NotificationManagerCompat.from(this)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(
                    CHANNEL_ID,
                    NotificationManagerCompat.IMPORTANCE_LOW,
                ).setName(CHANNEL_ID)
                    .build(),
            )

        ServiceCompat.startForeground(
            this,
            100,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(resources.getString(R.string.foreground_notif_title))
                .setContentText(resources.getString(R.string.foreground_notif_desc))
                .setSmallIcon(R.drawable.app_icon)
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        if (!hasReadExternalStorage) {
            logUtils.debugLog("No storage permissions, requesting", null)
            launchPermissionActivity()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        logUtils.debugLog("Binding $intent", null)
        return WallpaperServer(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()

        logUtils.debugLog("Stopping wallpaper server service", null)

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }
}