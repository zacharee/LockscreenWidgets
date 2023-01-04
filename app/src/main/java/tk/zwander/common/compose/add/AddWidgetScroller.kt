package tk.zwander.common.compose.add

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.common.data.AppInfo
import tk.zwander.common.util.getRemoteDrawable
import tk.zwander.common.util.toBitmap
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo

@Composable
fun AddWidgetScroller(
    filteredItems: List<AppInfo>,
    onSelected: (BaseListInfo<*>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
    ) {
        items(items = filteredItems, key = { it.appInfo.packageName }) { app ->
            Column(modifier = Modifier.fillMaxWidth()) {
                AppHeader(
                    app = app,
                    modifier = Modifier
                        .fillMaxWidth()
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(
                        items = app.widgets.toList(),
                        key = { it.itemInfo.hashCode() }
                    ) { widget ->
                        val icon = icon(
                            info = widget,
                            key = widget.itemInfo.provider
                        )

                        WidgetItem(
                            image = icon,
                            label = widget.itemInfo.loadLabel(context.packageManager),
                            subLabel = "${widget.itemInfo.minWidth}x${widget.itemInfo.minHeight}"
                        ) {
                            onSelected(widget)
                        }
                    }

                    items(
                        items = app.shortcuts.toList(),
                        key = { it.itemInfo.activityInfo.run { ComponentName(packageName, name) } }
                    ) { shortcut ->
                        val icon = icon(
                            info = shortcut,
                            key = shortcut.itemInfo.activityInfo.componentName
                        )

                        WidgetItem(
                            image = icon,
                            label = shortcut.itemInfo.loadLabel(context.packageManager)
                                .toString(),
                            subLabel = stringResource(id = R.string.shortcut)
                        ) {
                            onSelected(shortcut)
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier,
        shape = RectangleShape
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
                mutableStateOf<Bitmap?>(null)
            }

            LaunchedEffect(key1 = app.appInfo.packageName) {
                icon = withContext(Dispatchers.IO) {
                    context.packageManager.getApplicationIcon(app.appInfo.packageName)
                        .mutate().toBitmap()
                }
            }

            Image(
                bitmap = icon?.asImageBitmap()
                    ?: ContextCompat.getDrawable(
                        context, R.drawable.ic_launcher_foreground
                    )!!.toBitmap().asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier.size(36.dp)
            )

            Text(text = app.appName)
        }
    }
}

@Composable
private fun icon(
    info: BaseListInfo<*>,
    key: Any?,
): Bitmap? {
    val context = LocalContext.current

    var icon by remember(key) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(key) {
        icon = withContext(Dispatchers.IO) {
            context.getRemoteDrawable(
                info.appInfo.packageName,
                info.icon
            )
        }
    }

    return icon
}
