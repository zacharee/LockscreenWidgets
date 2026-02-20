package tk.zwander.lockscreenwidgets.activities

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.getAllInstalledWidgetProviders
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.setThemedContent
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

            Surface(
                modifier = Modifier.fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = {
                                AddWidgetStackWidgetActivity.start(this@WidgetStackConfigure, widgetId)
                            },
                        ) {
                            Text(text = "Add Widget")
                        }

                        Button(
                            onClick = {
                                val intent = Intent(this@WidgetStackConfigure, WidgetStackProvider::class.java)
                                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                sendBroadcast(intent)
                                finish()
                            },
                        ) {
                            Text(text = "Done")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(items = widgetStackWidgets, key = { it.id }) { widget ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(
                                    text = widget.id.toString(),
                                )

                                Button(
                                    onClick = {
                                        openWidgetConfig(widget)
                                    },
                                ) {
                                    Text(text = "Configure")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openWidgetConfig(currentData: WidgetData) {
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
}
