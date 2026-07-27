package tk.zwander.common.compose.main

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.bugsnag.android.Bugsnag
import kotlinx.coroutines.launch
import tk.zwander.common.activities.settings.CommonSettingsActivity
import tk.zwander.common.compose.components.ClickableCard
import tk.zwander.common.compose.components.ContentCard
import tk.zwander.common.compose.components.PreferenceSwitch
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.util.PrefManager
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R

@Preview
@Composable
fun DebugCard(
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current

    ContentCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.more),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
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

        val bugsnagEnabled by rememberBooleanPreferenceState(
            key = PrefManager.KEY_ENABLE_BUGSNAG,
        )

        AnimatedVisibility(
            visible = bugsnagEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ClickableCard(
                title = stringResource(R.string.bugsnag_user_id),
                summary = Bugsnag.getUser().id,
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            clipEntry = ClipEntry(
                                clipData = ClipData.newPlainText(
                                    "user_id",
                                    Bugsnag.getUser().id,
                                ),
                            ),
                        )
                    }
                },
                endIcon = painterResource(R.drawable.copy_all_24px),
            )
        }

        ClickableCard(
            title = stringResource(R.string.more_settings),
            summary = null,
            onClick = {
                context.startActivity(Intent(context, CommonSettingsActivity::class.java))
            },
            endIcon = painterResource(R.drawable.chevron_right_24px),
        )
    }
}
