package tk.zwander.common.compose.main

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import tk.zwander.common.compose.components.CardSwitch
import tk.zwander.common.compose.data.FeatureCardInfo
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.data.MainPageButton
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.EventObserverEffect
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.ComposeFrameSettingsActivity
import tk.zwander.lockscreenwidgets.activities.UsageActivity
import tk.zwander.widgetdrawer.activities.ComposeDrawerSettingsActivity

@Composable
fun rememberFeatureCards(): List<FeatureCardInfo> {
    val context = LocalContext.current

    return remember {
        listOf(
            FeatureCardInfo(
                title = R.string.app_name,
                version = BuildConfig.VERSION_NAME,
                enabledLabel = R.string.enabled,
                disabledLabel = R.string.disabled,
                enabledKey = PrefManager.KEY_WIDGET_FRAME_ENABLED,
                buttons = listOf(
                    MainPageButton(
                        R.drawable.ic_baseline_preview_24,
                        R.string.preview
                    ) {
                        context.eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.TOGGLE))
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
                        context.startActivity(Intent(context, ComposeFrameSettingsActivity::class.java))
                    },
                ),
                onAddWidget = {
                    context.eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION, 100))
                },
                isEnabled = { context.prefManager.widgetFrameEnabled },
                onEnabledChanged = { context.prefManager.widgetFrameEnabled = it },
                eventObserver = object : EventObserver {
                    override fun onEvent(event: Event) {
                        if (event is Event.FrameSelected) {
                            if (event.frameId != null && event.requestCode == 100) {
                                context.eventManager.sendEvent(Event.LaunchAddWidget(event.frameId))
                            }
                        }
                    }
                },
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
                        context.startActivity(Intent(context, ComposeDrawerSettingsActivity::class.java))
                    }
                ),
                { context.eventManager.sendEvent(Event.LaunchAddDrawerWidget(false)) },
                { context.prefManager.drawerEnabled },
                { context.prefManager.drawerEnabled = it }
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureCard(info: FeatureCardInfo) {
    EventObserverEffect(info.eventObserver)

    Card(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = info.title),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge,
            )

            Box(modifier = Modifier.fillMaxWidth(0.25f)) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            var enabled by rememberBooleanPreferenceState(
                key = info.enabledKey,
                enabled = { info.isEnabled() },
                onEnabledChanged = { _, v -> info.onEnabledChanged(v) },
            )

            CardSwitch(
                enabled = enabled,
                onEnabledChanged = { enabled = it },
                title = stringResource(id = if (enabled) info.enabledLabel else info.disabledLabel),
            )

            AnimatedVisibility(visible = enabled) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.size(16.dp))

                    SubduedOutlinedButton(
                        onClick = {
                            info.onAddWidget()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_baseline_add_24),
                            contentDescription = stringResource(id = R.string.add_widget),
                            contentScale = ContentScale.FillHeight,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Text(
                            text = stringResource(id = R.string.add_widget),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }

                    Spacer(Modifier.size(16.dp))

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val width = this.maxWidth

                        var maxItemHeight by remember(info.buttons.toList()) {
                            mutableIntStateOf(0)
                        }
                        var minTextSize by remember(info.buttons.toList()) {
                            mutableStateOf(16.sp)
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val itemsPerRow = info.buttons.size.coerceAtMost(3)
                            val columnWidth = (width - (12.dp * (itemsPerRow - 1))) / itemsPerRow

                            info.buttons.forEachIndexed { index, mainPageButton ->
                                ExtraButton(
                                    info = mainPageButton,
                                    modifier = Modifier
                                        .width(columnWidth)
                                        .padding(
                                            start = if (index == 0) 0.dp else 4.dp,
                                            end = if (index == info.buttons.lastIndex) 0.dp else 4.dp,
                                        )
                                        .onSizeChanged { s ->
                                            if (s.height > maxItemHeight) {
                                                maxItemHeight = s.height
                                            }
                                        }
                                        .then(
                                            if (maxItemHeight > 0) {
                                                with(LocalDensity.current) {
                                                    Modifier.height(maxItemHeight.toDp())
                                                }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    onFontSizeCalculated = { min ->
                                        if (minTextSize.isUnspecified || min < minTextSize) {
                                            minTextSize = min
                                        }
                                    },
                                    maxFontSize = minTextSize,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}