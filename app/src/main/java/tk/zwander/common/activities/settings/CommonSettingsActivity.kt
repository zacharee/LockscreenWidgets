package tk.zwander.common.activities.settings

import android.net.Uri
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.settings.PreferenceScreen
import tk.zwander.common.compose.settings.rememberPreferenceScreen
import tk.zwander.common.util.*
import tk.zwander.common.util.contracts.rememberCreateDocumentLauncherWithDownloadFallback
import tk.zwander.lockscreenwidgets.R
import java.text.SimpleDateFormat
import java.util.*

private data class FrameDataItem(
    @StringRes val title: Int,
    val key: String,
    val action: suspend CoroutineScope.() -> Unit,
)

class CommonSettingsActivity : BaseActivity() {
    private fun writeLog(uri: Uri?) {
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.let { logUtils.exportLog(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setThemedContent {
            val scope = rememberCoroutineScope()
            val resources = LocalResources.current
            val context = LocalContext.current

            var pendingAction by remember {
                mutableStateOf<FrameDataItem?>(null)
            }

            val debugExportLauncher = rememberCreateDocumentLauncherWithDownloadFallback(
                mimeType = "text/plain",
            ) { uri: Uri? ->
                writeLog(uri)
            }

            val preferenceScreen = rememberPreferenceScreen {
                category(
                    key = "general",
                    title = resources.getString(R.string.general),
                ) {
                    seekBarPreference(
                        title = { stringResource(R.string.settings_screen_accessibility_event_delay) },
                        summary = { stringResource(R.string.settings_screen_accessibility_event_delay_desc) },
                        icon = { painterResource(R.drawable.ic_baseline_timer_24) },
                        key = { PrefManager.KEY_ACCESSIBILITY_EVENT_DELAY },
                        defaultValue = { 50 },
                        minValue = { 0 },
                        maxValue = { 5000 },
                        unit = { stringResource(R.string.unit_milliseconds) },
                        scale = { 1.0 },
                    )
                }

                category(
                    key = "debug",
                    title = resources.getString(R.string.category_debug),
                ) {
                    switchPreference(
                        title = { stringResource(R.string.settings_screen_debug_log) },
                        summary = { stringResource(id = R.string.settings_screen_debug_log_desc) },
                        icon = { painterResource(R.drawable.document_search_24px) },
                        key = { PrefManager.KEY_DEBUG_LOG },
                    )

                    switchPreference(
                        title = { stringResource(R.string.settings_screen_show_debug_id_view) },
                        summary = { stringResource(id = R.string.settings_screen_show_debug_id_view_desc) },
                        icon = { painterResource(R.drawable.notes_24px) },
                        key = { PrefManager.KEY_SHOW_DEBUG_ID_VIEW },
                    )

                    preference(
                        title = { stringResource(id = R.string.settings_screen_export_debug_log) },
                        summary = { stringResource(id = R.string.settings_screen_export_debug_log_desc)},
                        icon = { painterResource(R.drawable.ic_baseline_save_24) },
                        key = { "export_debug_logs" },
                        onClick = {
                            val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
                            val fileName = "lockscreen_widgets_debug_${formatter.format(Date())}.txt"

                            debugExportLauncher.launch(fileName)
                        },
                        defaultValue = { null },
                    )

                    preference(
                        title = { stringResource(id = R.string.settings_screen_clear_debug_log) },
                        summary = { stringResource(id = R.string.settings_screen_clear_debug_log_desc) },
                        icon = { painterResource(R.drawable.scan_delete_24px) },
                        key = { "clear_debug_logs" },
                        onClick = {
                            context.logUtils.resetDebugLog()
                        },
                        defaultValue = { null },
                    )
                }

                category(
                    key = "reset_frame_size_and_position",
                    title = resources.getString(R.string.reset_frame_position_size),
                ) {
                    val items = [
                        FrameDataItem(
                            title = R.string.on_lock_screen,
                            key = "reset_lock_screen_frame",
                            action = {
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.LockNormal.Portrait,
                                )
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.LockNormal.Landscape,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.LockNormal.Portrait,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.LockNormal.Landscape,
                                )

                                context.prefManager.currentSecondaryFramesWithStringDisplay.forEach { [frameId] ->
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockscreen.Landscape(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockscreen.Landscape(frameId),
                                    )
                                }
                            },
                        ),
                        FrameDataItem(
                            title = R.string.in_notification_center,
                            key = "reset_nc_frame",
                            action = {
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.NotificationNormal.Portrait,
                                )
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.NotificationNormal.Landscape,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.NotificationNormal.Portrait,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.NotificationNormal.Landscape,
                                )

                                context.prefManager.currentSecondaryFramesWithStringDisplay.forEach { [frameId] ->
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryNotification.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryNotification.Landscape(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryNotification.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryNotification.Landscape(frameId),
                                    )
                                }
                            },
                        ),
                        FrameDataItem(
                            title = R.string.in_locked_notification_center,
                            key = "reset_locked_nc_frame",
                            action = {
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.LockNotification.Portrait,
                                )
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.LockNotification.Landscape,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.LockNotification.Portrait,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.LockNotification.Landscape,
                                )

                                context.prefManager.currentSecondaryFramesWithStringDisplay.forEach { [frameId] ->
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockNotification.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockNotification.Landscape(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockNotification.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryLockNotification.Landscape(frameId),
                                    )
                                }
                            },
                        ),
                        FrameDataItem(
                            title = R.string.in_previews,
                            key = "reset_preview_frame",
                            action = {
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.Preview.Portrait,
                                )
                                context.frameSizeAndPosition.removeSizeForType(
                                    FrameSizeAndPosition.FrameType.Preview.Landscape,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.Preview.Portrait,
                                )
                                context.frameSizeAndPosition.removePositionForType(
                                    FrameSizeAndPosition.FrameType.Preview.Landscape,
                                )

                                context.prefManager.currentSecondaryFramesWithStringDisplay.forEach { [frameId] ->
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryPreview.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removeSizeForType(
                                        FrameSizeAndPosition.FrameType.SecondaryPreview.Landscape(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryPreview.Portrait(frameId),
                                    )
                                    context.frameSizeAndPosition.removePositionForType(
                                        FrameSizeAndPosition.FrameType.SecondaryPreview.Landscape(frameId),
                                    )
                                }
                            },
                        )
                    ]

                    items.forEach { item ->
                        preference(
                            title = { stringResource(item.title) },
                            summary = { null },
                            icon = { null },
                            defaultValue = { null },
                            onClick = {
                                pendingAction = item
                            },
                            key = { item.key },
                        )
                    }
                }
            }

            PreferenceScreen(
                title = stringResource(R.string.more_settings),
                categories = preferenceScreen,
            )

            pendingAction?.let { action ->
                AlertDialog(
                    onDismissRequest = {
                        pendingAction = null
                    },
                    title = {
                        Text(text = stringResource(id = action.title))
                    },
                    text = {
                        Text(text = stringResource(id = R.string.reset_frame_position_size_confirm_message))
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch(block = action.action)
                                pendingAction = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(text = stringResource(id = R.string.yes))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { pendingAction = null }
                        ) {
                            Text(text = stringResource(id = R.string.no))
                        }
                    },
                )
            }
        }
    }
}
