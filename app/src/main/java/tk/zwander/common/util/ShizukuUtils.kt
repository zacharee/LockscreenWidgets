package tk.zwander.common.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import tk.zwander.lockscreenwidgets.IShizukuService
import tk.zwander.lockscreenwidgets.app
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object ShizukuUtils {
    @Composable
    fun rememberShizukuInstallStateAsState(): State<Boolean> {
        val context = LocalContext.current

        val isInstalled = remember {
            mutableStateOf(context.isShizukuInstalled)
        }

        LifecycleEffect(Lifecycle.State.RESUMED) {
            isInstalled.value = context.isShizukuInstalled
        }

        DisposableEffect(null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    isInstalled.value = context.isShizukuInstalled
                }
            }

            val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED)
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED)

            context.registerReceiver(receiver, filter)

            onDispose {
                context.unregisterReceiver(receiver)
            }
        }

        return isInstalled
    }

    @Composable
    fun rememberShizukuRunningStateAsState(): State<Boolean> {
        val isRunning = remember {
            mutableStateOf(isShizukuRunning)
        }

        LifecycleEffect(Lifecycle.State.RESUMED) {
            isRunning.value = isShizukuRunning
        }

        DisposableEffect(null) {
            val listener = Shizuku.OnBinderReceivedListener {
                isRunning.value = isShizukuRunning
            }

            Shizuku.addBinderReceivedListenerSticky(listener)

            onDispose {
                Shizuku.removeBinderReceivedListener(listener)
            }
        }

        return isRunning
    }

    val isShizukuRunning: Boolean
        get() = Shizuku.pingBinder()

    val Context.isShizukuInstalled: Boolean
        get() = try {
            packageManager.getPackageInfo(ShizukuProvider.MANAGER_APPLICATION_ID, 0)
            true
        } catch (e: Throwable) {
            false
        }

    suspend fun Context.runShizukuCommand(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: IShizukuService.() -> Unit,
    ): ShizukuCommandResult = coroutineScope {
        if (isShizukuRunning) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                app.postShizukuCommand(coroutineContext, block)
                ShizukuCommandResult.POSTED
            } else {
                withContext(Dispatchers.IO) {
                    val granted = suspendCoroutine { cont ->
                        val listener = object : Shizuku.OnRequestPermissionResultListener {
                            override fun onRequestPermissionResult(
                                requestCode: Int,
                                grantResult: Int,
                            ) {
                                Shizuku.removeRequestPermissionResultListener(this)
                                cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                            }
                        }
                        Shizuku.addRequestPermissionResultListener(listener)
                        Shizuku.requestPermission(100)
                    }

                    if (granted) {
                        app.postShizukuCommand(coroutineContext, block)
                        ShizukuCommandResult.POSTED
                    } else {
                        ShizukuCommandResult.PERMISSION_DENIED
                    }
                }
            }
        } else {
            if (isShizukuInstalled) {
                ShizukuCommandResult.INSTALLED_NOT_RUNNING
            } else {
                ShizukuCommandResult.NOT_INSTALLED
            }
        }
    }
}

enum class ShizukuCommandResult {
    NOT_INSTALLED,
    INSTALLED_NOT_RUNNING,
    PERMISSION_DENIED,
    POSTED,
}