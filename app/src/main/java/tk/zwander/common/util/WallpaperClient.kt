package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.zwander.lswinterconnect.IWallpaperAPI
import dev.zwander.lswinterconnect.peekLogUtils
import dev.zwander.lswinterconnect.safeApplicationContext

val Context.wallpaperClient: WallpaperClient
    get() = WallpaperClient.getInstance(this)

class WallpaperClient private constructor(private val context: Context) {
    companion object {
        private const val COMPANION_PKG = "dev.zwander.lswwallpaper"

        @SuppressLint("StaticFieldLeak")
        private var instance: WallpaperClient? = null

        fun getInstance(context: Context): WallpaperClient {
            return instance ?: WallpaperClient(context.safeApplicationContext).also {
                instance = it
            }
        }
    }

    private val serviceIntent = Intent().apply {
        `package` = COMPANION_PKG
        component = ComponentName(COMPANION_PKG, "${COMPANION_PKG}.WallpaperServerService")
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?,
        ) {
            peekLogUtils?.debugLog("Got service", null)
            context.wallpaperUtils.wallpaperServerConnected(
                IWallpaperAPI.Stub.asInterface(service),
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            peekLogUtils?.debugLog("Lost service", null)
            context.wallpaperUtils.wallpaperServerDisconnected()
            tryBindService()
        }
    }

    private val companionInstallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.data?.host == COMPANION_PKG) {
                tryBindService()
            }
        }
    }

    val isServerInstalled: Boolean
        get() = try {
            context.packageManager.getPackageInfo(COMPANION_PKG, 0) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    init {
        tryBindService()

        ContextCompat.registerReceiver(
            context,
            companionInstallReceiver,
            IntentFilter().apply {
                [
                    Intent.ACTION_PACKAGE_REPLACED,
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_CHANGED,
                    Intent.ACTION_PACKAGES_UNSUSPENDED,
                    Intent.ACTION_PACKAGE_UNSTOPPED,
                    Intent.ACTION_PACKAGE_UNSUSPENDED_MANUALLY,
                    Intent.ACTION_PACKAGE_RESTARTED,
                ].forEach { addAction(it) }
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun tryBindService() {
        try {
            context.unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {}

        peekLogUtils?.debugLog("Trying bind", null)
        val result = context.bindService(
            serviceIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE or
                    Context.BIND_IMPORTANT or
                    Context.BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE or
                    Context.BIND_FOREGROUND_SERVICE or
                    Context.BIND_SHOWING_UI or
                    Context.BIND_ABOVE_CLIENT or
                    Context.BIND_ALLOW_ACTIVITY_STARTS or
                    Context.BIND_INCLUDE_CAPABILITIES,
        )
        peekLogUtils?.debugLog("bound? $result", null)
    }
}
