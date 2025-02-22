package tk.zwander.common.util.shizuku

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import tk.zwander.common.util.LifecycleEffect
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.safeApplicationContext
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.IShizukuService
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val Context.shizukuManager: ShizukuManager
    get() = ShizukuManager.getInstance(this)

class ShizukuManager private constructor(private val context: Context) : CoroutineScope by MainScope() {
    companion object {
        val isShizukuRunning: Boolean
            get() = Shizuku.pingBinder()

        @SuppressLint("StaticFieldLeak")
        private var instance: ShizukuManager? = null

        @Synchronized
        fun getInstance(context: Context): ShizukuManager {
            return instance ?: ShizukuManager(context.safeApplicationContext).apply {
                instance = this
            }
        }

        @Composable
        fun rememberShizukuInstallStateAsState(): State<Boolean> {
            val context = LocalContext.current

            val isInstalled = remember {
                mutableStateOf(context.shizukuManager.isShizukuInstalled)
            }

            LifecycleEffect(Lifecycle.State.RESUMED) {
                isInstalled.value = context.shizukuManager.isShizukuInstalled
            }

            DisposableEffect(null) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent?) {
                        isInstalled.value = context.shizukuManager.isShizukuInstalled
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
    }

    val isShizukuInstalled: Boolean
        get() = try {
            context.packageManager.getPackageInfo(ShizukuProvider.MANAGER_APPLICATION_ID, 0)
            true
        } catch (e: Throwable) {
            false
        }

    private var postMutex = Mutex()
    private val queuedCommands = ArrayList<Pair<CoroutineContext, IShizukuService.() -> Unit>>()
    private var userService: IShizukuService? = null
        set(value) {
            synchronized(postMutex) {
                field = value

                if (value != null) {
                    queuedCommands.forEach { (context, command) ->
                        launch(context) {
                            value.command()
                        }
                    }
                    queuedCommands.clear()
                }
            }
        }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            userService = IShizukuService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, ShizukuService::class.java))
            .version(BuildConfig.VERSION_CODE + (if (BuildConfig.DEBUG) 101 else 0))
            .processNameSuffix("granter")
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)
            .tag("${context.packageName}_granter")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun onCreate() {
        Shizuku.addBinderReceivedListenerSticky {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                addUserService()
            } else {
                Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            addUserService()
                            Shizuku.removeRequestPermissionResultListener(this)
                        }
                    }
                })
            }
        }
    }

    fun launchShizuku(): Boolean {
        return try {
            context.startActivity(context.packageManager.getLaunchIntentForPackage(ShizukuProvider.MANAGER_APPLICATION_ID))
            true
        } catch (e: Exception) {
            context.logUtils.normalLog("Unable to open Shizuku.", e)
            false
        }
    }

    suspend fun runShizukuCommand(
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: IShizukuService.() -> Unit,
    ): ShizukuCommandResult = coroutineScope {
        try {
            if (isShizukuRunning) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    postShizukuCommand(coroutineContext, block)
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
                            postShizukuCommand(coroutineContext, block)
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
        } catch (e: IllegalStateException) {
            context.logUtils.normalLog("Unable to post Shizuku command", e)
            ShizukuCommandResult.ERROR
        }
    }

    private fun postShizukuCommand(context: CoroutineContext, command: IShizukuService.() -> Unit) {
        if (userService != null) {
            launch(context) {
                userService?.let(command) ?: synchronized(postMutex) {
                    queuedCommands.add(context to command)
                }
            }
        } else {
            synchronized(postMutex) {
                queuedCommands.add(context to command)
            }
        }
    }

    private fun addUserService() {
        try {
            Shizuku.unbindUserService(
                serviceArgs,
                userServiceConnection,
                true,
            )

            Shizuku.bindUserService(
                serviceArgs,
                userServiceConnection,
            )
        } catch (e: IllegalStateException) {
            context.logUtils.normalLog("Unable to bind Shizuku service", e)
        }
    }
}

enum class ShizukuCommandResult {
    NOT_INSTALLED,
    INSTALLED_NOT_RUNNING,
    PERMISSION_DENIED,
    POSTED,
    ERROR,
    ;
}
