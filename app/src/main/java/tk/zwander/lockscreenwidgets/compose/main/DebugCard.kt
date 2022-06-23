package tk.zwander.lockscreenwidgets.compose.main

import android.net.Uri
import android.view.animation.AnticipateOvershootInterpolator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.compose.components.ClickableCard
import tk.zwander.lockscreenwidgets.compose.components.PreferenceSwitch
import tk.zwander.lockscreenwidgets.util.PrefManager
import tk.zwander.lockscreenwidgets.util.logUtils
import java.text.SimpleDateFormat
import java.util.*

@Preview
@Composable
fun DebugCard() {
    val context = LocalContext.current

    val onDebugExportResult = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
        if (uri != null) {
            with(context) {
                logUtils.exportLog(contentResolver.openOutputStream(uri))
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var expanded by remember {
                mutableStateOf(false)
            }

            val rotation by animateFloatAsState(
                targetValue = if (expanded) 0f else 180f,
                animationSpec = tween(
                    easing = {
                        AnticipateOvershootInterpolator().getInterpolation(it)
                    }
                )
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.category_debug),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.h4,
                )

                Spacer(Modifier.size(if (expanded) 8.dp else 0.dp))

                AnimatedVisibility(visible = expanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val debugEnabled = PreferenceSwitch(
                            key = PrefManager.KEY_DEBUG_LOG,
                            title = stringResource(id = R.string.settings_screen_debug_log),
                            summary = stringResource(id = R.string.settings_screen_debug_log_desc)
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
                                onDebugExportResult.launch("lockscreen_widgets_debug_${formatter.format(Date())}.txt")
                            }
                        )

                        ClickableCard(
                            title = stringResource(id = R.string.settings_screen_clear_debug_log),
                            summary = stringResource(id = R.string.settings_screen_clear_debug_log_desc),
                            onClick = {
                                context.logUtils.resetDebugLog()
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { expanded = !expanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_up),
                        contentDescription = stringResource(id = R.string.expand),
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }
    }
}