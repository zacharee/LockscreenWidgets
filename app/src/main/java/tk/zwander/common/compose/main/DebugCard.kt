package tk.zwander.common.compose.main

import android.content.Context
import android.net.Uri
import android.view.animation.AnticipateOvershootInterpolator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.components.ClearFrameDataCard
import tk.zwander.common.compose.components.ClickableCard
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
fun DebugCard() {
    val context = LocalContext.current

    val debugExportLauncher = rememberCreateDocumentLauncherWithDownloadFallback(
        mimeType = "text/plain",
    ) { uri: Uri? ->
        context.writeLog(uri)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var expanded by rememberSaveable {
                mutableStateOf(false)
            }

            val rotation by animateFloatAsState(
                targetValue = if (expanded) 0f else 180f,
                animationSpec = tween(
                    easing = {
                        AnticipateOvershootInterpolator().getInterpolation(it)
                    },
                ),
                label = "expandedRotation",
            )

            Column(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
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

                Spacer(modifier = Modifier.size(8.dp))

                PreferenceSwitch(
                    key = PrefManager.KEY_ENABLE_BUGSNAG,
                    title = stringResource(id = R.string.debug_enable_bugsnag),
                    summary = stringResource(id = R.string.debug_enable_bugsnag_desc),
                    defaultValue = true,
                )

                AnimatedVisibility(
                    visible = expanded,
                    modifier = Modifier.wrapContentHeight(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(modifier = Modifier.size(8.dp))

                        val debugEnabled = PreferenceSwitch(
                            key = PrefManager.KEY_DEBUG_LOG,
                            title = stringResource(id = R.string.settings_screen_debug_log),
                            summary = stringResource(id = R.string.settings_screen_debug_log_desc),
                        )

                        AnimatedVisibility(visible = debugEnabled) {
                            PreferenceSwitch(
                                key = PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
                                title = stringResource(id = R.string.settings_screen_show_debug_id_view),
                                summary = stringResource(id = R.string.settings_screen_show_debug_id_view_desc),
                            )
                        }

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
                    }
                }
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp,
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent,
                    ),
                    elevation = CardDefaults.outlinedCardElevation(),
                    onClick = { expanded = !expanded },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_up),
                            contentDescription = stringResource(id = R.string.expand),
                            modifier = Modifier.rotate(rotation),
                        )
                    }
                }
            }
        }
    }
}