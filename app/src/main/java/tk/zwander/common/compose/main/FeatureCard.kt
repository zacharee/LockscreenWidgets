package tk.zwander.common.compose.main

import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.lifecycle.Lifecycle
import tk.zwander.common.activities.WidgetStackListActivity
import tk.zwander.common.compose.components.CardSwitch
import tk.zwander.common.compose.components.ContentCard
import tk.zwander.common.compose.data.ActionInfo
import tk.zwander.common.compose.data.EnabledInfo
import tk.zwander.common.compose.data.FeatureCardInfo
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.data.MainPageButton
import tk.zwander.common.util.*
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.ComposeFrameSettingsActivity
import tk.zwander.lockscreenwidgets.activities.UsageActivity
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider
import tk.zwander.lockscreenwidgets.compose.SelectDisplayDialog
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate
import tk.zwander.widgetdrawer.activities.ComposeDrawerSettingsActivity
import tk.zwander.widgetdrawer.util.DrawerDelegate

@Composable
fun rememberFeatureCards(): Map<String, List<FeatureCardInfo>> {
    val context = LocalContext.current

    var showingDisplaySelectorAddFrameWidget by remember {
        mutableStateOf(false)
    }

    var widgetStackIds by remember {
        mutableStateOf<List<Int>>([])
    }

    LifecycleEffect(Lifecycle.State.RESUMED) {
        widgetStackIds = context.appWidgetManager.getAppWidgetIds(
            ComponentName(context, WidgetStackProvider::class.java),
        ).toList()
    }

    if (showingDisplaySelectorAddFrameWidget) {
        SelectDisplayDialog(
            dismiss = {
                showingDisplaySelectorAddFrameWidget = false
            },
            onFrameSelected = {
                context.eventManager.sendEvent(Event.LaunchAddWidget(it))
                showingDisplaySelectorAddFrameWidget = false
            },
        )
    }

    val updatedWidgetStackIds by rememberUpdatedState(widgetStackIds)

    val items by remember {
        derivedStateOf {
            val map = mutableMapOf<String, List<FeatureCardInfo>>()

            map["MainFeatures"] = [
                FeatureCardInfo(
                    title = R.string.lock_screen_widgets,
                    description = R.string.lock_screen_widgets_desc,
                    enabled = EnabledInfo(
                        enabledLabel = R.string.enabled,
                        disabledLabel = R.string.disabled,
                        key = PrefManager.KEY_WIDGET_FRAME_ENABLED,
                        isEnabled = { context.prefManager.widgetFrameEnabled },
                        onEnabledChanged = { context.prefManager.widgetFrameEnabled = it },
                    ),
                    buttons = [
                        MainPageButton(
                            icon = R.drawable.ic_baseline_preview_24,
                            title = R.string.preview,
                            dependency = { MainWidgetFrameDelegate.readOnlyInstance.collectAsState().value != null },
                        ) {
                            context.eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.TOGGLE))
                        },
                        MainPageButton(
                            icon = R.drawable.ic_baseline_help_outline_24,
                            title = R.string.usage,
                        ) {
                            context.startActivity(Intent(context, UsageActivity::class.java))
                        },
                        MainPageButton(
                            icon = R.drawable.ic_baseline_settings_24,
                            title = R.string.settings,
                        ) {
                            context.startActivity(Intent(context, ComposeFrameSettingsActivity::class.java))
                        },
                    ],
                    action = ActionInfo(
                        label = R.string.add_widget,
                        icon = R.drawable.ic_baseline_add_24,
                    ) {
                        context.eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.HIDE))

                        if (!BuildConfig.DEBUG && context.prefManager.currentSecondaryFramesWithStringDisplay.isEmpty()) {
                            context.eventManager.sendEvent(Event.LaunchAddWidget(MainWidgetFrameDelegate.ID))
                        } else {
                            showingDisplaySelectorAddFrameWidget = true
                        }
                    },
                ),
                FeatureCardInfo(
                    title = R.string.widget_drawer,
                    description = R.string.widget_drawer_desc,
                    enabled = EnabledInfo(
                        enabledLabel = R.string.enabled,
                        disabledLabel = R.string.disabled,
                        key = PrefManager.KEY_DRAWER_ENABLED,
                        isEnabled = { context.prefManager.drawerEnabled },
                        onEnabledChanged = { context.prefManager.drawerEnabled = it },
                    ),
                    buttons = [
                        MainPageButton(
                            icon = R.drawable.ic_baseline_open_in_new_24,
                            title = R.string.open_drawer,
                            dependency = { DrawerDelegate.readOnlyInstance.collectAsState().value != null },
                        ) {
                            context.eventManager.sendEvent(Event.ShowDrawer)
                        },
                        MainPageButton(
                            icon = R.drawable.ic_baseline_settings_24,
                            title = R.string.settings,
                        ) {
                            context.startActivity(Intent(context, ComposeDrawerSettingsActivity::class.java))
                        },
                    ],
                    action = ActionInfo(
                        label = R.string.add_widget,
                        icon = R.drawable.ic_baseline_add_24,
                    ) {
                        context.eventManager.sendEvent(
                            Event.LaunchAddDrawerWidget(false),
                        )
                    },
                ),
            ]

            map["ExtraFeatures"] = [
                FeatureCardInfo(
                    title = R.string.widget_stacks,
                    description = if (updatedWidgetStackIds.isEmpty()) {
                        R.string.widget_stacks_desc_empty
                    } else {
                        R.string.widget_stacks_desc
                    },
                    action = if (updatedWidgetStackIds.isNotEmpty()) {
                        ActionInfo(
                            label = R.string.manage_widget_stacks,
                            icon = R.drawable.ic_baseline_layers_24,
                        ) {
                            context.startActivity(
                                Intent(context, WidgetStackListActivity::class.java),
                            )
                        }
                    } else {
                        null
                    },
                ),
                if (context.isOneUI) {
                    FeatureCardInfo(
                        title = R.string.one_ui_widget_tiles_title,
                        description = R.string.one_ui_widget_tiles_desc,
                    )
                } else {
                    null
                },
            ].filterNotNull()

            map
        }
    }

    return items
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureCard(
    info: FeatureCardInfo,
    modifier: Modifier = Modifier,
) {
    EventObserverEffect(info.eventObserver)

    ContentCard(
        modifier = modifier.fillMaxWidth(),
        verticalSpacing = 0.dp,
    ) {
        Text(
            text = stringResource(id = info.title),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (info.description != null) {
            Text(
                text = stringResource(info.description),
                textAlign = TextAlign.Center,
            )
        }

        if (info.enabled != null ||
                info.buttons.isNotEmpty() ||
                info.action != null) {
            Box(modifier = Modifier.fillMaxWidth(0.25f)) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        var enabled by if (info.enabled != null) {
            rememberBooleanPreferenceState(
                key = info.enabled.key,
                enabled = { info.enabled.isEnabled() },
                onEnabledChanged = { _, v -> info.enabled.onEnabledChanged(v) },
            )
        } else {
            remember {
                mutableStateOf(true)
            }
        }

        info.enabled?.let {
            CardSwitch(
                enabled = enabled,
                onEnabledChanged = { enabled = it },
                title = stringResource(
                    id = if (enabled || info.enabled.disabledLabel == null) {
                        info.enabled.enabledLabel
                    } else {
                        info.enabled.disabledLabel
                    },
                ),
            )
        }

        AnimatedVisibility(
            visible = (enabled || info.enabled == null) &&
                    (info.action != null || info.buttons.isNotEmpty()),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                info.enabled?.let {
                    Spacer(Modifier.size(16.dp))
                }

                info.action?.let {
                    SubduedOutlinedButton(
                        onClick = {
                            info.action.onAction()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                    ) {
                        info.action.icon?.let {
                            Image(
                                painter = painterResource(id = it),
                                contentDescription = stringResource(id = info.action.label),
                                contentScale = ContentScale.FillHeight,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.size(16.dp))
                        Text(
                            text = stringResource(id = info.action.label),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }

                if (info.buttons.isNotEmpty()) {
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
