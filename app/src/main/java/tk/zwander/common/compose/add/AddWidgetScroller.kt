package tk.zwander.common.compose.add

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.data.AppInfo
import tk.zwander.common.util.componentNameCompat
import tk.zwander.common.util.loadPreviewOrIconDrawable
import tk.zwander.common.util.logUtils
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo

@Composable
fun AddWidgetScroller(
    filteredItems: List<AppInfo>,
    onSelected: (BaseListInfo<*>) -> Unit,
    searchBarHeight: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier,
        contentPadding = WindowInsets
            .systemBars
            .add(WindowInsets.ime)
            .add(WindowInsets(top = searchBarHeight))
            .add(WindowInsets(top = 8.dp))
            .asPaddingValues(),
    ) {
        items(items = filteredItems, key = { it.appInfo.packageName }) { app ->
            Column(modifier = Modifier.fillMaxWidth()) {
                AppHeader(
                    app = app,
                    modifier = Modifier
                        .fillMaxWidth(),
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(
                        items = app.widgets.toList(),
                        key = { it.itemInfo.hashCode() },
                    ) { widget ->
                        val icon = icon(
                            info = widget,
                            key = widget.itemInfo.provider,
                        )

                        val previewLayout = remember(widget.itemInfo.provider) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    widget.itemInfo.previewLayout.takeIf { it != 0 }?.let {
                                        val contextForProvider = context.createApplicationContext(widget.itemInfo.providerInfo.applicationInfo, 0)

                                        LayoutInflater.from(contextForProvider).inflate(it, null)
                                    }
                                } catch (e: Throwable) {
                                    context.logUtils.debugLog("Unable to inflate widget preview.", e)
                                    null
                                }
                            } else {
                                null
                            }
                        }

                        WidgetItem(
                            image = icon,
                            previewLayout = previewLayout,
                            label = widget.itemInfo.loadLabel(context.packageManager),
                            subLabel = "${widget.itemInfo.minWidth}x${widget.itemInfo.minHeight}",
                        ) {
                            onSelected(widget)
                        }
                    }

                    items(
                        items = app.shortcuts.toList(),
                        key = { it.itemInfo.activityInfo.componentNameCompat },
                    ) { shortcut ->
                        val icon = icon(
                            info = shortcut,
                            key = shortcut.itemInfo.activityInfo.componentNameCompat,
                        )

                        WidgetItem(
                            image = icon,
                            label = shortcut.itemInfo.loadLabel(context.packageManager)
                                .toString(),
                            subLabel = stringResource(id = R.string.shortcut),
                        ) {
                            onSelected(shortcut)
                        }
                    }

                    items(
                        items = app.launcherItems.toList(),
                        key = { it.itemInfo.activityInfo.componentNameCompat.flattenToString() + "launcher_item" },
                    ) { launcherItem ->
                        val icon = icon(
                            info = launcherItem,
                            key = launcherItem.itemInfo.activityInfo.componentNameCompat,
                        )

                        WidgetItem(
                            image = icon,
                            label = app.appName,
                            subLabel = stringResource(R.string.launcher),
                        ) {
                            onSelected(launcherItem)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        items(
                            items = app.launcherShortcuts.toList(),
                            key = { it.itemInfo.id },
                        ) { shortcut ->
                            val icon = icon(
                                info = shortcut,
                                key = shortcut.itemInfo.id,
                            )

                            WidgetItem(
                                image = icon,
                                label = shortcut.name,
                                subLabel = stringResource(id = R.string.shortcut),
                            ) {
                                onSelected(shortcut)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    app: AppInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier,
        shape = RectangleShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var icon by remember(app.appInfo.packageName) {
                mutableStateOf<Drawable?>(null)
            }

            LaunchedEffect(key1 = app.appInfo.packageName) {
                icon = withContext(Dispatchers.IO) {
                    try {
                        app.appInfo.loadIcon(context.packageManager)
                            .mutate()
                    } catch (e: Throwable) {
                        context.logUtils.normalLog("Unable to load app icon for ${app.appInfo.packageName}", e)
                        null
                    }
                }
            }

            val image = icon

            Image(
                painter = if (image != null) {
                    rememberDrawablePainter(image)
                } else {
                    painterResource(id = R.drawable.ic_launcher_foreground)
                },
                contentDescription = app.appName,
                modifier = Modifier.size(36.dp),
            )

            Text(text = app.appName)
        }
    }
}

@Composable
private fun icon(
    info: BaseListInfo<*>,
    key: Any?,
): Drawable? {
    val context = LocalContext.current

    var icon by remember(key) {
        mutableStateOf<Drawable?>(null)
    }

    LaunchedEffect(key) {
        icon = withContext(Dispatchers.IO) {
            val previewOrIcon = if (info is WidgetListInfo) {
                info.itemInfo.loadPreviewOrIconDrawable(context)
            } else null

            previewOrIcon ?: try {
                info.icon?.loadDrawable(context)
            } catch (e: PackageManager.NameNotFoundException) {
                context.logUtils.normalLog(
                    "Unable to load icon for ${info.appInfo.appInfo.packageName}, ${key}.",
                    e,
                )
                null
            } catch (e: NullPointerException) {
                context.logUtils.normalLog(
                    "Unable to load icon for ${info.appInfo.appInfo.packageName}, ${key}.",
                    e,
                )
                null
            } catch (e: OutOfMemoryError) {
                context.logUtils.normalLog(
                    "Unable to load icon for ${info.appInfo.appInfo.packageName}, ${key}.",
                    e,
                )
                null
            } catch (_: Resources.NotFoundException) {
                info.appInfo.appInfo.loadIcon(context.packageManager)
            } ?: info.appInfo.appInfo.loadIcon(context.packageManager)
        }
    }

    return icon
}
