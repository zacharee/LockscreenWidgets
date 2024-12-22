package tk.zwander.common.compose.data

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import dev.zwander.composeintroslider.IntroPage
import dev.zwander.composeintroslider.SimpleIntroPage
import kotlinx.coroutines.launch
import rikka.shizuku.ShizukuProvider
import tk.zwander.common.activities.OnboardingActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.util.LifecycleEffect
import tk.zwander.common.util.ShizukuUtils
import tk.zwander.common.util.canReadWallpaper
import tk.zwander.common.util.launchUrl
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.services.isAccessibilityEnabled
import tk.zwander.lockscreenwidgets.services.isNotificationListenerActive
import tk.zwander.lockscreenwidgets.services.openAccessibilitySettings

@Composable
fun rememberIntroSlides(
    startReason: OnboardingActivity.RetroMode,
    finish: () -> Unit,
): List<IntroPage> {
    val context = LocalContext.current
    val slides = remember(startReason) {
        mutableStateListOf<IntroPage>()
    }
    val scope = rememberCoroutineScope()

    var hasAccessibility by remember {
        mutableStateOf(context.isAccessibilityEnabled)
    }
    var hasNotificationAccess by remember {
        mutableStateOf(
            startReason != OnboardingActivity.RetroMode.NOTIFICATION ||
                    context.isNotificationListenerActive
        )
    }
    var canReadWallpaper by remember {
        mutableStateOf(
            startReason != OnboardingActivity.RetroMode.STORAGE ||
                    context.canReadWallpaper
        )
    }

    val storagePermissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) {
            canReadWallpaper = startReason != OnboardingActivity.RetroMode.STORAGE ||
                    context.canReadWallpaper
        }

    LifecycleEffect(Lifecycle.State.RESUMED) {
        hasAccessibility = context.isAccessibilityEnabled
        hasNotificationAccess = startReason != OnboardingActivity.RetroMode.NOTIFICATION ||
                context.isNotificationListenerActive
        canReadWallpaper = startReason != OnboardingActivity.RetroMode.STORAGE ||
                context.canReadWallpaper
    }

    DisposableEffect(key1 = startReason) {
        val listenUri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)

                if (listenUri == uri) {
                    hasAccessibility = context.isAccessibilityEnabled
                }
            }
        }

        context.contentResolver.registerContentObserver(
            listenUri,
            true,
            contentObserver
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(contentObserver)
        }
    }

    DisposableEffect(key1 = startReason) {
        val listenUri = Settings.Secure.getUriFor("enabled_notification_listeners")
        val contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)

                if (listenUri == uri) {
                    hasNotificationAccess =
                        startReason != OnboardingActivity.RetroMode.NOTIFICATION ||
                                context.isNotificationListenerActive
                }
            }
        }

        context.contentResolver.registerContentObserver(
            listenUri,
            true,
            contentObserver
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(contentObserver)
        }
    }

    LaunchedEffect(key1 = startReason) {
        if (startReason == OnboardingActivity.RetroMode.NONE) {
            slides.add(
                SimpleIntroPage(
                    title = { stringResource(id = R.string.intro_welcome_title) },
                    description = { stringResource(id = R.string.intro_welcome_desc) },
                    slideColor = { colorResource(id = R.color.slide_1) },
                    contentColor = { colorResource(id = R.color.slide_1_text) },
                    icon = { painterResource(id = R.drawable.ic_baseline_right_hand_24) },
                )
            )

            slides.add(SimpleIntroPage(
                title = { stringResource(id = R.string.intro_usage_title) },
                description = { stringResource(id = R.string.intro_usage_desc) },
                slideColor = { colorResource(id = R.color.slide_2) },
                contentColor = { colorResource(id = R.color.slide_2_text) },
                icon = { painterResource(id = R.drawable.ic_baseline_gesture_two_tap) },
            ))

            slides.add(SimpleIntroPage(
                title = { stringResource(id = R.string.privacy_policy) },
                description = { stringResource(id = R.string.intro_privacy_policy_desc) },
                slideColor = { colorResource(id = R.color.slide_3) },
                contentColor = { colorResource(id = R.color.slide_3_text) },
                icon = { painterResource(id = R.drawable.info) },
                extraContent = {
                    OutlinedButton(
                        onClick = {
                            context.launchUrl("https://github.com/zacharee/LockscreenWidgets/blob/master/PRIVACY.md")
                        }
                    ) {
                        Text(text = stringResource(id = R.string.more_info))
                    }
                },
            ))
        }

        if (startReason == OnboardingActivity.RetroMode.NONE ||
            startReason == OnboardingActivity.RetroMode.ACCESSIBILITY
        ) {
            slides.add(SimpleIntroPage(
                title = { stringResource(id = R.string.intro_accessibility_title) },
                description = { stringResource(id = R.string.accessibility_service_desc) },
                slideColor = { colorResource(id = R.color.slide_4) },
                contentColor = { colorResource(id = R.color.slide_4_text) },
                icon = { painterResource(id = R.drawable.ic_baseline_accessibility_new_24) },
                extraContent = {
                    var showingDialog by remember {
                        mutableStateOf(false)
                    }

                    OutlinedButton(
                        onClick = { showingDialog = true },
                        enabled = !hasAccessibility,
                    ) {
                        Text(text = stringResource(id = if (hasAccessibility) R.string.granted else R.string.more_info))
                    }

                    if (showingDialog) {
                        AppTheme {
                            AlertDialog(
                                onDismissRequest = { showingDialog = false },
                                title = { Text(text = stringResource(id = R.string.intro_accessibility_title)) },
                                text = { Text(text = stringResource(id = R.string.intro_accessibility_desc)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            context.openAccessibilitySettings()
                                            showingDialog = false
                                        }
                                    ) {
                                        Text(text = stringResource(id = R.string.grant))
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = finish
                                    ) {
                                        Text(text = stringResource(id = R.string.close_app))
                                    }
                                }
                            )
                        }
                    }
                },
                canMoveForward = { hasAccessibility || BuildConfig.DEBUG },
            ))
        }

        if (startReason == OnboardingActivity.RetroMode.NONE ||
            startReason == OnboardingActivity.RetroMode.NOTIFICATION
        ) {
            slides.add(SimpleIntroPage(
                title = { stringResource(id = R.string.intro_notification_listener_title) },
                description = { stringResource(id = R.string.intro_notification_listener_desc) },
                slideColor = { colorResource(id = R.color.slide_5) },
                contentColor = { colorResource(id = R.color.slide_5_text) },
                icon = { painterResource(id = R.drawable.ic_baseline_notifications_active_24) },
                extraContent = {
                    OutlinedButton(
                        onClick = {
                            val notifIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(notifIntent)
                        },
                        enabled = !hasNotificationAccess,
                    ) {
                        Text(text = stringResource(id = if (hasNotificationAccess) R.string.granted else R.string.grant))
                    }
                },
                canMoveForward = { hasNotificationAccess || BuildConfig.DEBUG },
            ))
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1 &&
            (startReason == OnboardingActivity.RetroMode.NONE || startReason == OnboardingActivity.RetroMode.STORAGE)) {
            slides.add(SimpleIntroPage(
                title = { stringResource(id = R.string.intro_read_storage_title) },
                description = {
                    stringResource(id = R.string.intro_read_storage_desc).run {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            "$this ${stringResource(id = R.string.intro_read_external_storage_desc_13_addendum)}"
                        } else {
                            this
                        }
                    }
                },
                slideColor = { colorResource(id = R.color.slide_6) },
                contentColor = { colorResource(id = R.color.slide_6_text) },
                icon = { painterResource(id = R.drawable.ic_baseline_sd_storage_24) },
                extraContent = {
                    val shizukuInstalled by ShizukuUtils.rememberShizukuInstallStateAsState()
                    val shizukuRunning by ShizukuUtils.rememberShizukuRunningStateAsState()

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (shizukuInstalled && shizukuRunning) {
                                    scope.launch {
                                        with(ShizukuUtils) {
                                            context.runShizukuCommand {
                                                grantReadExternalStorage()
                                                canReadWallpaper = context.canReadWallpaper
                                            }
                                        }
                                    }
                                } else if (shizukuInstalled) {
                                    try {
                                        context.startActivity(context.packageManager.getLaunchIntentForPackage(ShizukuProvider.MANAGER_APPLICATION_ID))
                                    } catch (_: Exception) {}
                                } else {
                                    context.launchUrl("https://shizuku.rikka.app/download/")
                                }
                            } else {
                                storagePermissionLauncher.launch(
                                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                )
                            }
                        },
                        enabled = !canReadWallpaper,
                    ) {
                        Text(
                            text = stringResource(
                                id = if (canReadWallpaper) {
                                    R.string.granted
                                } else {
                                    when {
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                                            if (shizukuInstalled && shizukuRunning) {
                                                R.string.grant
                                            } else if (shizukuInstalled) {
                                                R.string.open_shizuku
                                            } else {
                                                R.string.install_shizuku
                                            }
                                        }

                                        else -> {
                                            R.string.grant
                                        }
                                    }
                                },
                            ),
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            context.launchUrl("https://github.com/zacharee/LockscreenWidgets/blob/master/PRIVACY.md")
                        }
                    ) {
                        Text(text = stringResource(id = R.string.privacy_policy))
                    }
                },
                canMoveForward = { canReadWallpaper || BuildConfig.DEBUG },
            ))
        }

        if (startReason == OnboardingActivity.RetroMode.NONE ||
            startReason == OnboardingActivity.RetroMode.BATTERY
        ) {
            slides.add(SimpleIntroPage(
                title = { stringResource(id = R.string.intro_battery_optimization) },
                description = { stringResource(id = R.string.intro_battery_optimization_desc) },
                slideColor = { colorResource(id = R.color.slide_7) },
                contentColor = { colorResource(id = R.color.slide_7_text) },
                icon = { painterResource(id = R.drawable.ic_baseline_battery_alert_24) },
                extraContent = {
                    OutlinedButton(
                        onClick = {
                            context.launchUrl("https://dontkillmyapp.com/?app=Lockscreen%20Widgets")
                        }
                    ) {
                        Text(text = stringResource(id = R.string.more_info))
                    }
                },
            ))
        }

        if (startReason == OnboardingActivity.RetroMode.NONE) {
            slides.add(SimpleIntroPage(
                title = { stringResource(id = R.string.intro_done_title) },
                description = { stringResource(id = R.string.intro_done_desc) },
                slideColor = { colorResource(id = R.color.slide_8) },
                contentColor = { colorResource(id = R.color.slide_8_text) },
                icon = { painterResource(id = R.drawable.ic_baseline_done_24) },
            ))
        }
    }

    return slides
}
