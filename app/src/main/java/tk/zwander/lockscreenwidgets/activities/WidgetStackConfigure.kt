package tk.zwander.lockscreenwidgets.activities

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.add.WidgetItem
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.compose.util.widgetViewCacheRegistry
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.getAllInstalledWidgetProviders
import tk.zwander.common.util.loadPreviewOrIconDrawable
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.setThemedContent
import tk.zwander.common.util.themedContext
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.AddWidgetStackWidgetActivity
import tk.zwander.lockscreenwidgets.activities.add.WidgetStackReconfigureActivity
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider

class WidgetStackConfigure : BaseActivity() {
    private val widgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (widgetId == -1) {
            setResult(
                RESULT_CANCELED,
                Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                },
            )
            finish()
            return
        } else {
            setResult(
                RESULT_OK,
                Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                },
            )
        }

        setThemedContent {
            var widgetStackWidgets by rememberPreferenceState(
                key = PrefManager.KEY_WIDGET_STACK_WIDGETS,
                value = {
                    (prefManager.widgetStackWidgets[widgetId] ?: LinkedHashSet()).toMutableList()
                },
                onChanged = { _, value ->
                    val newWidgets = HashMap(prefManager.widgetStackWidgets)
                    newWidgets[widgetId] = LinkedHashSet(value)

                    prefManager.widgetStackWidgets = newWidgets
                },
            )

            Content(
                widgetId = widgetId,
                onFinish = {
                    setResult(
                        if (it) RESULT_OK else RESULT_CANCELED,
                        Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        },
                    )
                    WidgetStackProvider.update(this, intArrayOf(widgetId))
                    finish()
                },
                widgets = widgetStackWidgets,
                onWidgetsChange = {
                    widgetStackWidgets = it.toMutableList()
                },
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun Content(
    widgetId: Int,
    widgets: List<WidgetData>,
    onWidgetsChange: (List<WidgetData>) -> Unit,
    onFinish: (success: Boolean) -> Unit,
) {
    val context = LocalContext.current

    var widgetPendingRemoval by remember {
        mutableStateOf<WidgetData?>(null)
    }

    var localWidgetList by remember {
        mutableStateOf(widgets)
    }

    LaunchedEffect(widgets) {
        localWidgetList = widgets
    }

    Surface(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(
                insets = WindowInsets.systemBars.only(
                    WindowInsetsSides.Bottom +
                            WindowInsetsSides.Start +
                            WindowInsetsSides.End,
                ),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            val listState = rememberLazyListState()
            val reorderableListState = rememberReorderableLazyListState(
                lazyListState = listState,
            ) { from, to ->
                val newWidgets = localWidgetList.toMutableList()
                newWidgets.add(to.index, newWidgets.removeAt(from.index))

                localWidgetList = newWidgets
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
                    .weight(1f),
                contentPadding = WindowInsets.systemBars
                    .only(WindowInsetsSides.Top).asPaddingValues(),
                state = listState,
            ) {
                items(items = localWidgetList, key = { it.id }) { widget ->
                    ReorderableItem(
                        state = reorderableListState,
                        key = widget.id,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .heightIn(min = 56.dp)
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp,
                                ),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = widget.label.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                    )

                                    Text(
                                        text = "(${widget.id})",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }

                                val providerInfo by remember {
                                    derivedStateOf {
                                        context.appWidgetManager.getAppWidgetInfo(widget.id)
                                    }
                                }

                                var widgetView by remember(widget.id) {
                                    mutableStateOf<View?>(null)
                                }

                                LaunchedEffect(widget.id) {
                                    widgetView = context.widgetViewCacheRegistry.getOrCreateView(
                                        context = context.themedContext,
                                        appWidget = providerInfo,
                                        appWidgetId = widget.id,
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    WidgetItem(
                                        image = icon(
                                            info = providerInfo,
                                            key = widget.id,
                                        ),
                                        previewLayout = widgetView,
                                        label = null,
                                        subLabel = null,
                                        modifier = Modifier.weight(1f),
                                        itemModifier = Modifier.heightIn(min = 150.dp)
                                            .padding(8.dp)
                                            .fillMaxWidth(),
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        IconButton(
                                            onClick = {
                                                context.openWidgetConfig(widget)
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.edit_24px),
                                                contentDescription = stringResource(R.string.configure_widget),
                                            )
                                        }

                                        Icon(
                                            painter = painterResource(R.drawable.menu_24px),
                                            contentDescription = stringResource(R.string.reorder_widget),
                                            modifier = Modifier.draggableHandle(),
                                        )

                                        IconButton(
                                            onClick = {
                                                widgetPendingRemoval = widget
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_baseline_delete_24),
                                                contentDescription = stringResource(R.string.remove),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.align(Alignment.End)
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    )
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onFinish(false)
                    },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }

                OutlinedButton(
                    onClick = {
                        AddWidgetStackWidgetActivity.start(context, widgetId)
                    },
                ) {
                    Text(text = stringResource(R.string.add_widget))
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, WidgetStackProvider::class.java)
                        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        context.sendBroadcast(intent)

                        onWidgetsChange(localWidgetList)
                        onFinish(true)
                    },
                ) {
                    Text(text = stringResource(R.string.apply))
                }
            }
        }
    }

    widgetPendingRemoval?.let { pendingRemoval ->
        AlertDialog(
            onDismissRequest = {
                widgetPendingRemoval = null
            },
            title = {
                Text(text = stringResource(R.string.alert_remove_widget_confirm))
            },
            text = {
                Text(text = stringResource(R.string.alert_remove_widget_confirm_desc))
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        widgetPendingRemoval = null
                    },
                ) {
                    Text(text = stringResource(R.string.no))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newWidgets = localWidgetList.toMutableList()
                        newWidgets.remove(pendingRemoval)

                        localWidgetList = newWidgets

                        widgetPendingRemoval = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.yes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

private fun Context.openWidgetConfig(currentData: WidgetData) {
    val provider = currentData.widgetProviderComponent

    if (provider == null) {
        Toast.makeText(this, R.string.error_reconfiguring_widget, Toast.LENGTH_SHORT)
            .show()
        logUtils.normalLog("Unable to reconfigure widget: provider is null.")
    } else {
        val pkg = provider.packageName
        val providerInfo = appWidgetManager.getAppWidgetInfo(currentData.id)
            ?: (getAllInstalledWidgetProviders(pkg)
                .find { info -> info.provider == provider })

        if (providerInfo == null) {
            Toast.makeText(this, R.string.error_reconfiguring_widget, Toast.LENGTH_SHORT)
                .show()
            logUtils.normalLog("Unable to reconfigure widget $provider: provider info is null.", null)
        } else {
            WidgetStackReconfigureActivity.launch(
                context = this,
                widgetId = currentData.id,
                providerInfo = providerInfo,
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun icon(
    info: AppWidgetProviderInfo?,
    key: Any?,
): Drawable? {
    val context = LocalContext.current
    val appInfo = info?.providerInfo?.applicationInfo ?: return null

    var icon by remember(key) {
        mutableStateOf<Drawable?>(null)
    }

    LaunchedEffect(key) {
        val appResources = context.packageManager.getResourcesForApplication(
            appInfo,
        )

        icon = withContext(Dispatchers.IO) {
            val previewOrIcon = info.loadPreviewOrIconDrawable(context)

            previewOrIcon ?: try {
                info.previewImage.run { if (this != 0) this else appInfo.icon }
                    .let { iconResource ->
                        try {
                            IconCompat.createWithResource(
                                appResources,
                                appInfo.packageName,
                                iconResource,
                            )
                        } catch (e: IllegalArgumentException) {
                            context.logUtils.debugLog("Error creating icon", e)
                            null
                        }
                    }?.loadDrawable(context)
            } catch (e: PackageManager.NameNotFoundException) {
                context.logUtils.normalLog(
                    "Unable to load icon for ${appInfo.packageName}, ${key}.",
                    e,
                )
                null
            } catch (e: NullPointerException) {
                context.logUtils.normalLog(
                    "Unable to load icon for ${appInfo.packageName}, ${key}.",
                    e,
                )
                null
            } catch (e: OutOfMemoryError) {
                context.logUtils.normalLog(
                    "Unable to load icon for ${appInfo.packageName}, ${key}.",
                    e,
                )
                null
            } catch (e: Resources.NotFoundException) {
                context.logUtils.normalLog(
                    "Unable to load icon for ${appInfo.packageName}, ${key}.",
                    e,
                )
                appInfo.loadIcon(context.packageManager)
            } ?: appInfo.loadIcon(context.packageManager)
        }
    }

    return icon
}

@Preview
@Composable
fun ConfigurePreview() {
    val context = LocalContext.current

    AppTheme {
        Content(
            widgetId = 0,
            onWidgetsChange = {},
            onFinish = {},
            widgets = listOf(
                WidgetData.widget(
                    context = context,
                    id = 1,
                    widgetProvider = ComponentName("com.test", ".Component"),
                    label = "Widget 1",
                    icon = null,
                    size = WidgetSizeData(1, 1),
                ),
                WidgetData.widget(
                    context = context,
                    id = 2,
                    widgetProvider = ComponentName("com.test", ".Component"),
                    label = "Widget 2",
                    icon = null,
                    size = WidgetSizeData(1, 1),
                ),
                WidgetData.widget(
                    context = context,
                    id = 3,
                    widgetProvider = ComponentName("com.test", ".Component"),
                    label = "Widget 3",
                    icon = null,
                    size = WidgetSizeData(1, 1),
                ),
                WidgetData.widget(
                    context = context,
                    id = 4,
                    widgetProvider = ComponentName("com.test", ".Component"),
                    label = "Widget 4",
                    icon = null,
                    size = WidgetSizeData(1, 1),
                ),
            ),
        )
    }
}
