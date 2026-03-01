package tk.zwander.common.activities

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.components.TitleBar
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.getApplicationInfoCompat
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.WidgetStackConfigure
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider

class WidgetStackListActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val context = LocalContext.current

                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    var widgetStacks by remember {
                        mutableStateOf<List<Int>>(listOf())
                    }

                    LaunchedEffect(null) {
                        widgetStacks = context.appWidgetManager.getAppWidgetIds(
                            ComponentName(context, WidgetStackProvider::class.java),
                        ).sorted()
                    }

                    Crossfade(
                        targetState = widgetStacks.isEmpty(),
                        modifier = Modifier.fillMaxSize(),
                    ) { empty ->
                        if (empty) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                TitleBar(
                                    title = stringResource(R.string.widget_stacks),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = WindowInsets.systemBars
                                        .only(WindowInsetsSides.Left + WindowInsetsSides.Right + WindowInsetsSides.Bottom)
                                        .asPaddingValues() + PaddingValues(top = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    itemsIndexed(items = widgetStacks, key = { _, id -> id }) { index, id ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 64.dp)
                                                .clickable {
                                                    context.startActivity(
                                                        Intent(
                                                            context,
                                                            WidgetStackConfigure::class.java
                                                        )
                                                            .putExtra(
                                                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                                id
                                                            ),
                                                    )
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(
                                                    text = "${stringResource(R.string.widget_stack)} $id",
                                                    style = MaterialTheme.typography.titleLarge,
                                                )

                                                val widgets = context.prefManager.widgetStackWidgets[id]
                                                    ?: setOf()

                                                widgets.forEach { widget ->
                                                    val widgetInfo = try {
                                                        context.appWidgetManager.getAppWidgetInfo(widget.id)
                                                    } catch (_: Throwable) {
                                                        null
                                                    }
                                                    val appInfo = try {
                                                        widgetInfo?.provider?.packageName?.let {
                                                            context.packageManager.getApplicationInfoCompat(
                                                                it
                                                            )
                                                        }
                                                    } catch (_: Throwable) {
                                                        null
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        Text(
                                                            text = "• ${widget.label}",
                                                            style = MaterialTheme.typography.titleMedium,
                                                        )

                                                        Text(
                                                            text = "${
                                                                appInfo?.loadLabel(
                                                                    context.packageManager
                                                                )
                                                            }",
                                                        )
                                                    }
                                                }
                                            }

                                            Icon(
                                                painter = painterResource(R.drawable.arrow_up),
                                                contentDescription = null,
                                                modifier = Modifier.rotate(90f),
                                            )
                                        }

                                        if (index < widgetStacks.lastIndex) {
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}