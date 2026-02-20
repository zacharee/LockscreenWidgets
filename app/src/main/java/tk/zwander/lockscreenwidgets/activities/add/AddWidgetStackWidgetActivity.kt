package tk.zwander.lockscreenwidgets.activities.add

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.unit.IntSize
import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.prefManager

class AddWidgetStackWidgetActivity : AddWidgetActivity() {
    private val widgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    }

    override val showShortcuts: Boolean = false
    override val gridSize: IntSize = IntSize(1, 1)
    override val colCount: Int = 1
    override val rowCount: Int = 1
    override var currentWidgets: MutableSet<WidgetData>
        get() = prefManager.widgetStackWidgets[widgetId] ?: LinkedHashSet()
        set(value) {
            val newWidgets = prefManager.widgetStackWidgets
            newWidgets[widgetId] = LinkedHashSet(value)

            prefManager.widgetStackWidgets = newWidgets
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (widgetId == -1) {
            finish()
        }

        super.onCreate(savedInstanceState)
    }

    companion object {
        fun start(context: Context, widgetId: Int) {
            val intent = Intent(context, AddWidgetStackWidgetActivity::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            context.startActivity(intent)
        }
    }
}
