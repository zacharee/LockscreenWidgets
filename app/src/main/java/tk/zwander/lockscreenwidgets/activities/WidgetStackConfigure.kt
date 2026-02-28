package tk.zwander.lockscreenwidgets.activities

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.ServiceManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.minus
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.IconCompat
import com.android.internal.appwidget.IAppWidgetService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.add.WidgetItem
import tk.zwander.common.compose.components.CardSwitch
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.compose.util.widgetViewCacheRegistry
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.getAllInstalledWidgetProviders
import tk.zwander.common.util.loadPreviewOrIconDrawable
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.setThemedContent
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.AddWidgetStackWidgetActivity
import tk.zwander.lockscreenwidgets.activities.add.WidgetStackReconfigureActivity
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider

private const val DEFAULT_CHANGE_DELAY_MS = 5000L
private const val MIN_CHANGE_DELAY_MS = 2000L

class WidgetStackConfigure : BaseActivity() {
    private val widgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(
            RESULT_CANCELED,
            Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            },
        )

        if (widgetId == -1) {
            finish()
            return
        }

        setThemedContent {
            val widgetStackWidgets by rememberPreferenceState(
                key = PrefManager.KEY_WIDGET_STACK_WIDGETS,
                value = {
                    (prefManager.widgetStackWidgets[widgetId] ?: LinkedHashSet()).toMutableList()
                },
                onChanged = { _, _ -> },
            )

            var autoChange by rememberPreferenceState(
                key = PrefManager.KEY_WIDGET_STACK_AUTO_CHANGE,
                value = { prefManager.widgetStackAutoChange[widgetId] ?: (false to DEFAULT_CHANGE_DELAY_MS) },
                onChanged = { _, value ->
                    val newAutoChange = prefManager.widgetStackAutoChange
                    newAutoChange[widgetId] = value
                    prefManager.widgetStackAutoChange = newAutoChange
                },
            )

            var widgetPadding by rememberPreferenceState(
                key = PrefManager.KEY_WIDGET_STACK_WIDGET_PADDING,
                value = { prefManager.widgetStackWidgetPadding[widgetId] ?: hashMapOf() },
                onChanged = { _, value ->
                    prefManager.widgetStackWidgetPadding = prefManager.widgetStackWidgetPadding.apply {
                        this[widgetId] = value
                    }
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
                    WidgetStackProvider.update(
                        context = this,
                        ids = intArrayOf(widgetId),
                    )
                    finish()
                },
                widgets = widgetStackWidgets,
                onWidgetsChange = {
                    it.forEach { widget ->
                        appWidgetManager.updateAppWidgetOptions(
                            widget.id,
                            appWidgetManager.getAppWidgetOptions(widgetId),
                        )
                    }

                    val newWidgets = HashMap(prefManager.widgetStackWidgets)
                    newWidgets[widgetId] = LinkedHashSet(it)

                    prefManager.widgetStackWidgets = newWidgets
                },
                autoChange = autoChange,
                onNewAutoChange = {
                    autoChange = it
                },
                widgetPadding = widgetPadding,
                onWidgetPaddingChange = {
                    widgetPadding = HashMap(it)
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
    autoChange: Pair<Boolean, Long>,
    widgetPadding: Map<Int, Boolean>,
    onWidgetsChange: (List<WidgetData>) -> Unit,
    onNewAutoChange: (Pair<Boolean, Long>) -> Unit,
    onFinish: (success: Boolean) -> Unit,
    onWidgetPaddingChange: (padding: Map<Int, Boolean>) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iAppWidgetService = remember {
        IAppWidgetService.Stub.asInterface(ServiceManager.getService(Context.APPWIDGET_SERVICE))
    }

    var eventCount by remember {
        mutableIntStateOf(0)
    }

    var widgetPendingRemoval by remember {
        mutableStateOf<WidgetData?>(null)
    }

    var localRemovedWidgets by remember {
        mutableStateOf<List<WidgetData>>(listOf())
    }

    var localAddedWidgets by remember {
        mutableStateOf<List<WidgetData>>(listOf())
    }

    var localWidgetList by remember {
        mutableStateOf(widgets)
    }

    var localAutoChange by remember {
        mutableStateOf(autoChange)
    }

    var localWidgetPadding by remember {
        mutableStateOf(widgetPadding)
    }

    val addWidgetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val addedWidget = it.data?.let { intent ->
            IntentCompat.getParcelableExtra(
                intent,
                AddWidgetStackWidgetActivity.EXTRA_ADDED_WIDGET,
                WidgetData::class.java,
            )
        }

        if (addedWidget != null) {
            localAddedWidgets = localAddedWidgets + addedWidget
            localWidgetList = localWidgetList + addedWidget
        }
    }

    LaunchedEffect(widgets) {
        localWidgetList = widgets.filterNot { localRemovedWidgets.contains(it) }
    }

    DisposableEffect(widgetId) {
        val listener = { event: Event.StackUpdateComplete ->
            if (event.stackId == widgetId) {
                eventCount++
            }
        }

        context.eventManager.addListener(listener)

        onDispose {
            context.eventManager.removeListener(listener)
        }
    }

    val systemBarsBottomPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
    val imeBottomPadding = WindowInsets.ime.only(WindowInsetsSides.Bottom)
        .takeIf { it.getBottom(density) > systemBarsBottomPadding.getBottom(density) }

    Surface(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(
                insets = WindowInsets.systemBars.only(
                        WindowInsetsSides.Start +
                            WindowInsetsSides.End,
                ).add(imeBottomPadding ?: systemBarsBottomPadding),
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

                                var widgetView by remember {
                                    mutableStateOf<AppWidgetHostView?>(null)
                                }

                                LaunchedEffect(eventCount) {
                                    widgetView?.updateAppWidget(
                                        iAppWidgetService.getAppWidgetViews(
                                            context.packageName,
                                            widget.id,
                                        ),
                                    )
                                }

                                LaunchedEffect(widget.id) {
                                    launch(Dispatchers.Main) {
                                        widgetView = context.widgetViewCacheRegistry.getOrCreateView(
                                            context = context,
                                            appWidget = providerInfo,
                                            appWidgetId = widget.id,
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        WidgetItem(
                                            image = icon(
                                                info = providerInfo,
                                                key = widget.id,
                                            ),
                                            previewLayout = widgetView,
                                            label = null,
                                            subLabel = null,
                                            itemModifier = Modifier
                                                .heightIn(min = 150.dp)
                                                .padding(8.dp)
                                                .fillMaxWidth(),
                                        )

                                        CompositionLocalProvider(
                                            LocalMinimumInteractiveComponentSize provides 32.dp,
                                        ) {
                                            CardSwitch(
                                                enabled = localWidgetPadding[widget.id] ?: false,
                                                onEnabledChanged = {
                                                    localWidgetPadding = localWidgetPadding.toMutableMap().apply {
                                                        this[widget.id] = it
                                                    }
                                                },
                                                title = stringResource(R.string.widget_padding),
                                                modifier = Modifier.fillMaxWidth()
                                                    .heightIn(min = 48.dp),
                                                titleTextStyle = MaterialTheme.typography.titleMedium,
                                                contentPadding = ButtonDefaults.ContentPadding - PaddingValues(horizontal = 12.dp),
                                            )
                                        }
                                    }

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

            Column(
                modifier = Modifier
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    )
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSwitch(
                    enabled = localAutoChange.first,
                    onEnabledChanged = {
                        localAutoChange = localAutoChange.copy(first = it)
                    },
                    title = stringResource(R.string.auto_cycle),
                    modifier = Modifier
                        .wrapContentHeight(),
                    titleTextStyle = MaterialTheme.typography.titleMedium,
                    accessory = {
                        var temporaryTextState by remember(autoChange.second) {
                            mutableStateOf(autoChange.second.toString())
                        }

                        TextField(
                            value = temporaryTextState,
                            onValueChange = { newValue ->
                                temporaryTextState = newValue.filter { it.isDigit() }
                                localAutoChange = localAutoChange
                                    .copy(
                                        second = temporaryTextState.toLongOrNull()
                                            ?.takeIf { it >= MIN_CHANGE_DELAY_MS }
                                            ?: localAutoChange.second,
                                    )
                            },
                            label = {
                                Text(text = stringResource(R.string.delay))
                            },
                            suffix = {
                                Text(text = stringResource(R.string.unit_milliseconds))
                            },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Number,
                            ),
                            modifier = Modifier
                                .widthIn(min = 0.dp)
                                .wrapContentHeight()
                                .weight(1f),
                            isError = temporaryTextState.toLongOrNull()
                                ?.takeIf { it >= MIN_CHANGE_DELAY_MS } == null,
                            colors = TextFieldDefaults.colors(
                                errorContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    },
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            localAddedWidgets.forEach {
                                context.widgetHostCompat.deleteAppWidgetId(it.id)
                            }
                            onFinish(false)
                        },
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }

                    OutlinedButton(
                        onClick = {
                            addWidgetLauncher.launch(
                                Intent(context, AddWidgetStackWidgetActivity::class.java)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                            )
                        },
                    ) {
                        Text(text = stringResource(R.string.add_widget))
                    }

                    OutlinedButton(
                        onClick = {
                            onWidgetsChange(localWidgetList)
                            onNewAutoChange(localAutoChange)
                            onWidgetPaddingChange(localWidgetPadding)

                            localRemovedWidgets.forEach {
                                context.widgetHostCompat.deleteAppWidgetId(it.id)
                            }
                            onFinish(true)
                        },
                    ) {
                        Text(text = stringResource(R.string.apply))
                    }
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
                        localRemovedWidgets = localRemovedWidgets + pendingRemoval

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
            onNewAutoChange = {},
            onFinish = {},
            onWidgetPaddingChange = {},
            autoChange = false to DEFAULT_CHANGE_DELAY_MS,
            widgetPadding = mapOf(),
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
