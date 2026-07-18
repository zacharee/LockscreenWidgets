package tk.zwander.common.compose.main

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import tk.zwander.common.compose.components.ClearFrameDataCard
import tk.zwander.common.compose.components.ClickableCard
import tk.zwander.common.compose.components.ContentCard
import tk.zwander.common.compose.components.PreferenceSwitch
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.contracts.rememberCreateDocumentLauncherWithDownloadFallback
import tk.zwander.common.util.logUtils
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Context.writeLog(uri: Uri?) {
    if (uri != null) {
        contentResolver.openOutputStream(uri)?.let { logUtils.exportLog(it) }
    }
}

@Preview
@Composable
fun DebugCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val debugExportLauncher = rememberCreateDocumentLauncherWithDownloadFallback(
        mimeType = "text/plain",
    ) { uri: Uri? ->
        context.writeLog(uri)
    }

    ContentCard(
        modifier = modifier.fillMaxWidth(),
        expandedContent = {
            PreferenceSwitch(
                key = PrefManager.KEY_DEBUG_LOG,
                title = stringResource(id = R.string.settings_screen_debug_log),
                summary = stringResource(id = R.string.settings_screen_debug_log_desc),
            )

            PreferenceSwitch(
                key = PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
                title = stringResource(id = R.string.settings_screen_show_debug_id_view),
                summary = stringResource(id = R.string.settings_screen_show_debug_id_view_desc),
            )

            ClickableCard(
                title = stringResource(id = R.string.settings_screen_export_debug_log),
                summary = stringResource(id = R.string.settings_screen_export_debug_log_desc),
                onClick = {
                    val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
                    val fileName = "lockscreen_widgets_debug_${formatter.format(Date())}.txt"

                    debugExportLauncher.launch(fileName)
                },
            )

            ClickableCard(
                title = stringResource(id = R.string.settings_screen_clear_debug_log),
                summary = stringResource(id = R.string.settings_screen_clear_debug_log_desc),
                onClick = {
                    context.logUtils.resetDebugLog()
                },
            )

            ClearFrameDataCard()
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.category_debug),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge,
            )

            Text(
                text = stringResource(id = R.string.version_template, BuildConfig.VERSION_NAME),
                textAlign = TextAlign.Center,
            )
        }

        PreferenceSwitch(
            key = PrefManager.KEY_ENABLE_BUGSNAG,
            title = stringResource(id = R.string.debug_enable_bugsnag),
            summary = stringResource(id = R.string.debug_enable_bugsnag_desc),
            defaultValue = true,
        )
    }
}