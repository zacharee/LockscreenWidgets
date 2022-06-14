package tk.zwander.lockscreenwidgets.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetTileInfo
import tk.zwander.lockscreenwidgets.util.prefManager

/**
 * Manage selecting the widget for a given tile.
 */
class AddTileWidgetActivity : AddWidgetActivity() {
    companion object {
        const val EXTRA_TILE_ID = "tile_id"

        fun createIntent(context: Context, tileId: Int): Intent {
            return Intent(context, AddTileWidgetActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_TILE_ID, tileId)
            }
        }
    }

    override val showShortcuts: Boolean
        get() = false

    override var currentWidgets: MutableSet<WidgetData>
        get() = mutableSetOf()
        set(_) {}

    override val currentIds: Collection<Int>
        get() = prefManager.customTiles.values.map { it.widgetId }

    private val tileId by lazy { intent.getIntExtra(EXTRA_TILE_ID, -1) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (tileId == -1) {
            finish()
            return
        }
    }

    /**
     * Add the specified widget to the frame and save it to SharedPreferences.
     *
     * @param id the ID of the widget to be added
     */
    override fun addNewWidget(id: Int, provider: AppWidgetProviderInfo) {
        prefManager.customTiles = prefManager.customTiles.apply {
            this[tileId] = WidgetTileInfo(id)
        }
        finish()
    }
}