package tk.zwander.common.compose.main

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.SizeMode
import tk.zwander.common.compose.components.CardSwitch
import tk.zwander.common.compose.data.FeatureCardInfo
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.data.MainPageButton
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.SettingsActivity
import tk.zwander.lockscreenwidgets.activities.UsageActivity
import tk.zwander.lockscreenwidgets.fragments.SettingsFragment
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate
import tk.zwander.widgetdrawer.fragments.DrawerSettings

@Composable
fun rememberFeatureCards(): List<FeatureCardInfo> {
    val context = LocalContext.current

    return remember {
        listOf(
            FeatureCardInfo(
                R.string.app_name,
                BuildConfig.VERSION_NAME,
                R.string.enabled,
                R.string.disabled,
                PrefManager.KEY_WIDGET_FRAME_ENABLED,
                listOf(
                    MainPageButton(
                        R.drawable.ic_baseline_preview_24,
                        R.string.preview
                    ) {
                        WidgetFrameDelegate.retrieveInstance(context)
                            ?.updateState { it.copy(isPreview = !it.isPreview) }
                    },
                    MainPageButton(
                        R.drawable.ic_baseline_help_outline_24,
                        R.string.usage
                    ) {
                        context.startActivity(Intent(context, UsageActivity::class.java))
                    },
                    MainPageButton(
                        R.drawable.ic_baseline_settings_24,
                        R.string.settings
                    ) {
                        SettingsActivity.launch(context, SettingsFragment::class.java)
                    },
                ),
                { context.eventManager.sendEvent(Event.LaunchAddWidget) },
                { context.prefManager.widgetFrameEnabled },
                { context.prefManager.widgetFrameEnabled = it }
            ),
            FeatureCardInfo(
                R.string.widget_drawer,
                BuildConfig.VERSION_NAME,
                R.string.enabled,
                R.string.disabled,
                PrefManager.KEY_DRAWER_ENABLED,
                listOf(
                    MainPageButton(
                        R.drawable.ic_baseline_open_in_new_24,
                        R.string.open_drawer
                    ) {
                        context.eventManager.sendEvent(Event.ShowDrawer)
                    },
                    MainPageButton(
                        R.drawable.ic_baseline_settings_24,
                        R.string.settings
                    ) {
                        SettingsActivity.launch(context, DrawerSettings::class.java)
                    }
                ),
                { context.eventManager.sendEvent(Event.LaunchAddDrawerWidget(false)) },
                { context.prefManager.drawerEnabled },
                { context.prefManager.drawerEnabled = it }
            )
        )
    }
}

@Composable
fun FeatureCard(info: FeatureCardInfo) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = info.title),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge
            )

            Box(modifier = Modifier.fillMaxWidth(0.25f)) {
                Divider(
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            var enabled by context.rememberBooleanPreferenceState(
                key = info.enabledKey,
                enabled = info.isEnabled,
                onEnabledChanged = info.onEnabledChanged
            )

            CardSwitch(
                enabled = enabled,
                onEnabledChanged = { enabled = it },
                title = stringResource(id = if (enabled) info.enabledLabel else info.disabledLabel)
            )

            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(Modifier.size(16.dp))

                    SubduedOutlinedButton(
                        onClick = {
                            info.onAddWidget()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_baseline_add_24),
                            contentDescription = stringResource(id = R.string.add_widget),
                            contentScale = ContentScale.FillHeight,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.size(16.dp))
                        Text(
                            text = stringResource(id = R.string.add_widget),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }

                    Spacer(Modifier.size(16.dp))

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val width = this.maxWidth

                        FlowRow(
                            mainAxisAlignment = FlowMainAxisAlignment.SpaceBetween,
                            crossAxisAlignment = FlowCrossAxisAlignment.Center,
                            mainAxisSpacing = 8.dp,
                            crossAxisSpacing = 8.dp,
                            mainAxisSize = SizeMode.Expand
                        ) {
                            val itemsPerRow = info.buttons.size.coerceAtMost(3)
                            val columnWidth = (width - (12.dp * (itemsPerRow - 1))) / itemsPerRow

                            info.buttons.forEach {
                                ExtraButton(
                                    info = it,
                                    modifier = Modifier.width(columnWidth)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}