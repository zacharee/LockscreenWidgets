package tk.zwander.common.compose.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import tk.zwander.common.activities.SelectIconPackActivity
import tk.zwander.common.util.backup.BackupRestoreManager
import tk.zwander.common.util.backup.backupRestoreManager
import tk.zwander.common.util.contracts.rememberCreateDocumentLauncherWithDownloadFallback
import tk.zwander.common.util.logUtils
import tk.zwander.lockscreenwidgets.R
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberPreferenceScreen(
    vararg keys: Any?,
    content: PreferenceScreenScope.() -> Unit,
): List<PreferenceCategory> {
    val context = LocalContext.current

    return remember(keys) {
        context.preferenceScreen(content)
    }
}

inline fun Context.preferenceScreen(content: PreferenceScreenScope.() -> Unit): List<PreferenceCategory> {
    val screenScope = PreferenceScreenScope(this)
    screenScope.content()

    return screenScope.categories
}

class PreferenceScreenScope(context: Context) : ContextWrapper(context) {
    val categories: MutableList<PreferenceCategory> = mutableListOf()

    fun category(
        key: String,
        title: String? = null,
        icon: Drawable? = null,
        collapsible: @Composable () -> Boolean = { title != null },
        content: PreferenceCategoryScope.() -> Unit,
    ) {
        val categoryScope = PreferenceCategoryScope(this)
        categoryScope.content()

        categories.add(
            PreferenceCategory(
                title = title,
                key = key,
                items = categoryScope.prefs,
                icon = icon,
                collapsible = collapsible,
            )
        )
    }
}

class PreferenceCategoryScope(context: Context) : ContextWrapper(context) {
    val prefs: MutableList<BasePreference<*>> = mutableListOf()

    fun preference(pref: BasePreference<*>) {
        prefs.add(pref)
    }

    fun <T> preference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        icon: @Composable () -> Painter?,
        defaultValue: @Composable () -> T,
        onClick: (() -> Unit)? = null,
        widget: (@Composable () -> Unit)? = null,
        widgetPosition: @Composable () -> WidgetPosition = { WidgetPosition.END },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
    ) {
        preference(
            BasePreference(
                title = title,
                summary = summary,
                key = key,
                icon = icon,
                defaultValue = defaultValue,
                onClick = onClick,
                widget = widget,
                widgetPosition = widgetPosition,
                enabled = enabled,
                visible = visible,
            ),
        )
    }

    fun switchPreference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        defaultValue: @Composable () -> Boolean = { false },
        icon: @Composable () -> Painter? = { null },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
        canChange: (Boolean) -> Boolean = { true },
    ) {
        preference(
            SwitchPreference(
                title = title,
                summary = summary,
                key = key,
                defaultValue = defaultValue,
                icon = icon,
                enabled = enabled,
                visible = visible,
                canChange = canChange,
            ),
        )
    }

    fun seekBarPreference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        defaultValue: @Composable () -> Int,
        minValue: @Composable () -> Int,
        maxValue: @Composable () -> Int,
        scale: @Composable () -> Double,
        icon: @Composable () -> Painter? = { null },
        unit: @Composable () -> String? = { null },
        increment: @Composable () -> Int = { 1 },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
    ) {
        preference(
            SeekBarPreference(
                title = title,
                summary = summary,
                key = key,
                defaultValue = defaultValue,
                minValue = minValue,
                maxValue = maxValue,
                scale = scale,
                icon = icon,
                unit = unit,
                increment = increment,
                enabled = enabled,
                visible = visible,
            ),
        )
    }

    fun colorPickerPreference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        defaultValue: @Composable () -> Int,
        icon: @Composable () -> Painter? = { null },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
    ) {
        preference(
            ColorPickerPreference(
                title = title,
                summary = summary,
                key = key,
                defaultValue = defaultValue,
                icon = icon,
                enabled = enabled,
                visible = visible,
            ),
        )
    }

    fun listPreference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        entries: @Composable () -> List<ListPreferenceEntry>,
        key: @Composable () -> String,
        defaultValue: @Composable () -> String?,
        icon: @Composable () -> Painter? = { null },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
    ) {
        preference(
            ListPreference(
                title = title,
                summary = summary,
                entries = entries,
                key = key,
                defaultValue = defaultValue,
                icon = icon,
                enabled = enabled,
                visible = visible,
            ),
        )
    }
}

@Composable
fun createCommonSection(which: BackupRestoreManager.Which): CommonSectionInfo {
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val backupLauncher = rememberCreateDocumentLauncherWithDownloadFallback(
        mimeType = "*/*",
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { output ->
                output.write(context.backupRestoreManager.createBackupString(which))
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val input = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()

                if (!context.backupRestoreManager.restoreBackupString(input, which)) {
                    Toast.makeText(context, R.string.unable_to_restore_widgets, Toast.LENGTH_LONG).show()
                    context.logUtils.normalLog("Unable to restore widgets")
                } else {
                    backDispatcher?.onBackPressed()
                }
            } catch (e: FileNotFoundException) {
                Toast.makeText(context, R.string.unable_to_restore_widgets, Toast.LENGTH_LONG).show()
                context.logUtils.normalLog("Unable to restore widgets", e)
            } catch (e: OutOfMemoryError) {
                Toast.makeText(context, R.string.unable_to_restore_widgets, Toast.LENGTH_LONG).show()
                context.logUtils.normalLog("Unable to restore widgets", e)
            }
        }
    }

    return CommonSectionInfo(
        backupLauncher = backupLauncher,
        restoreLauncher = restoreLauncher,
        which = which,
    )
}

data class CommonSectionInfo(
    val backupLauncher: ActivityResultLauncher<String>,
    val restoreLauncher: ActivityResultLauncher<Array<String>>,
    val which: BackupRestoreManager.Which,
) {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

    fun addToPreferenceScreen(screenScope: PreferenceScreenScope) {
        with (screenScope) {
            category(
                key = "common_category",
                title = null,
            ) {
                preference(
                    title = { stringResource(R.string.settings_screen_back_up_widgets) },
                    summary = { stringResource(R.string.settings_screen_back_up_widgets_desc) },
                    key = { "back_up_widgets" },
                    icon = { painterResource(R.drawable.ic_baseline_save_24) },
                    defaultValue = {},
                    onClick = {
                        try {
                            backupLauncher.launch(
                                "lockscreen_widgets_${which.name.lowercase()}_backup_${dateFormatter.format(Date())}.lswidg",
                            )
                        } catch (e: Exception) {
                            logUtils.normalLog("Unable to back up widgets", e)
                            Toast.makeText(this, R.string.unable_to_back_up_widgets, Toast.LENGTH_LONG).show()
                        }
                    },
                )

                preference(
                    title = { stringResource(R.string.settings_screen_restore_widgets) },
                    summary = { stringResource(R.string.settings_screen_restore_widgets_desc) },
                    key = { "restore_widgets" },
                    icon = { painterResource(R.drawable.ic_baseline_open_in_new_24) },
                    defaultValue = {},
                    onClick = {
                        try {
                            restoreLauncher.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            logUtils.normalLog("Unable to restore widgets", e)
                            Toast.makeText(this, R.string.unable_to_restore_widgets, Toast.LENGTH_LONG).show()
                        }
                    },
                )

                preference(
                    title = { stringResource(R.string.icon_pack) },
                    summary = { stringResource(R.string.icon_pack_desc) },
                    key = { "select_icon_pack" },
                    icon = { painterResource(R.drawable.image) },
                    defaultValue = {},
                    onClick = {
                        startActivity(Intent(this, SelectIconPackActivity::class.java))
                    },
                )
            }
        }
    }
}
