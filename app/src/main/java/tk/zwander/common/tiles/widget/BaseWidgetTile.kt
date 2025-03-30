package tk.zwander.common.tiles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetHost.AppWidgetHostListener
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.ServiceManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.common.activities.add.AddTileWidgetActivity
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.cropBitmapTransparency
import tk.zwander.common.util.density
import tk.zwander.common.util.getApplicationInfoInAnyState
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.textAsBitmap
import tk.zwander.common.util.toSafeBitmap
import tk.zwander.lockscreenwidgets.R
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents the base structure and logic for a widget tile on One UI.
 * Handles redirecting widget RemoteViews to Samsung's System UI.
 *
 * This is a very limited implementation. Samsung doesn't use an AppWidgetHost or
 * anything fancy to display the RemoteViews. Things like ListView or anything that uses
 * AppWidget-specific features (e.g., adapters) just won't work. Updates also don't happen
 * dynamically. The detail view has to be exited and re-entered to see changes.
 */
@Suppress("MemberVisibilityCanBePrivate")
@RequiresApi(Build.VERSION_CODES.N)
abstract class BaseWidgetTile : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {
    protected val iManager: IAppWidgetService by lazy {
        IAppWidgetService.Stub.asInterface(
            ServiceManager.getService(APPWIDGET_SERVICE)
        )
    }

    protected abstract val tileId: Int
    protected val widgetId: Int
        get() {
            val data = prefManager.customTiles[tileId] ?: return -1
            return data.widgetId
        }
    protected val widgetInfo: AppWidgetProviderInfo?
        get() {
            val widgetId = widgetId
            if (widgetId == -1) return null

            return appWidgetManager.getAppWidgetInfo(widgetId)
        }
    protected val widgetPackage: String?
        get() = widgetInfo?.providerInfo?.packageName
    protected val remoteResources: Resources?
        get() {
            return try {
                val packageName = widgetPackage ?: return null
                val appInfo = packageManager.getApplicationInfoInAnyState(packageName)
                packageManager.getResourcesForApplication(appInfo)
            } catch (_: Exception) {
                null
            }
        }

    protected val views by lazy { AtomicReference(generateViews()) }

    protected val appWidgetHostListener by lazy {
        try {
            object : AppWidgetHostListener {
                override fun onUpdateProviderInfo(appWidget: AppWidgetProviderInfo?) {
                    updateTile()
                    notifySystemUIOfChanges()
                }

                override fun onViewDataChanged(viewId: Int) {
                    notifySystemUIOfChanges()
                }

                override fun updateAppWidget(views: RemoteViews?) {
                    this@BaseWidgetTile.views.set(views?.getRemoteViewsToApply(this@BaseWidgetTile, null))
                    notifySystemUIOfChanges()
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun onStartListening() {
        super.onStartListening()

        updateTile()
        notifySystemUIOfChanges()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                appWidgetHostListener?.let {
                    widgetHostCompat.setListener(widgetId, it)
                }
            } catch (_: Throwable) {}
        }
        widgetHostCompat.startListening(this)
    }

    override fun onStopListening() {
        super.onStopListening()
        widgetHostCompat.stopListening(this)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()

        prefManager.customTiles = prefManager.customTiles.apply { remove(tileId) }
    }

    /**
     * The CharSequence returned here is displayed at the top
     * of the expanded detail view.
     */
    @Suppress("unused")
    open fun semGetDetailViewTitle(): CharSequence? {
        return qsTile?.label
    }

    /**
     * I don't really know what this is for.
     */
    @Suppress("unused")
    open fun semGetDetailViewSettingButtonName(): CharSequence? {
        return "Test"
    }

    /**
     * Return true if there should be an on/off switch present
     * in the expanded detail view.
     */
    @Suppress("unused")
    open fun semIsToggleButtonExists(): Boolean {
        return false
    }

    /**
     * Returns true if the switch is present.
     */
    @Suppress("unused")
    open fun semIsToggleButtonChecked(): Boolean {
        return false
    }

    /**
     * Get the widget's RemoteViews and pass it onto
     * Samsung's System UI.
     */
    @Suppress("unused")
    open fun semGetDetailView(): RemoteViews? {
        views.set(generateViews())
        return views.get()
    }

    /**
     * This is launched when the label of the QS tile is long-pressed.
     * Launch the widget selector for this tile.
     */
    open fun semGetSettingsIntent(): Intent? {
        return AddTileWidgetActivity.createIntent(this, tileId)
    }

    /**
     * Called when the switch in the expanded detail view is pressed.
     * By default, used to launch the widget selector for this tile.
     */
    @Suppress("unused")
    open fun semSetToggleButtonChecked(checked: Boolean) {
        startActivity(semGetSettingsIntent())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PrefManager.KEY_CUSTOM_TILES) {
            updateTile()
            notifySystemUIOfChanges()
        }
    }

    private fun notifySystemUIOfChanges() {
        try {
            TileService::class.java.getMethod("semUpdateDetailView")
                .invoke(this)
        } catch (e: Throwable) {
            logUtils.normalLog("Error updating widget view", e)
        }
    }

    /**
     * Generate a RemoteViews object for this tile.
     * If no widget is selected, this generates an "add" button that launches
     * the widget selector for this tile.
     */
    private fun generateViews(): RemoteViews {
        var outerView = generateDefaultViews()

        try {
            //Try to get the widget RemoteViews.
            val widgetView = iManager.getAppWidgetViews(packageName, widgetId)
            if (widgetView != null) {
                logUtils.debugLog("Custom widget loaded for tile ID $tileId")

                //Success, set it.
                outerView = widgetView.getRemoteViewsToApply(this, null)
            } else {
                logUtils.debugLog("Custom widget view is null for tile ID $tileId")
                //Error retrieving widget, or widget not selected.
            }
        } catch (e: Exception) {
            logUtils.debugLog("Exception adding widget for tile ID $tileId", e)
            //Error retrieving widget.
        }

        //Return either the default view or the selected widget.
        return outerView
    }

    /**
     * Create a RemoteViews for the add button.
     */
    private fun generateDefaultViews(): RemoteViews {
        val views = RemoteViews(packageName, R.layout.default_tile_views)
        views.setOnClickPendingIntent(
            R.id.add, PendingIntent.getActivity(
            this, 100, semGetSettingsIntent(), PendingIntent.FLAG_IMMUTABLE
        ))
        return views
    }

    /**
     * Update the QS tile with info from the selected widget. If there is
     * no widget selected, use the default icon and label.
     */
    private fun updateTile() {
        widgetInfo.apply {
            if (this != null) {
                with (remoteResources) {
                    if (this != null) {
                        val iconDrawable = ResourcesCompat.getDrawable(this, icon, this.newTheme())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && iconDrawable is AdaptiveIconDrawable) {
                            val foreground = iconDrawable.foreground
                            qsTile?.icon = Icon.createWithBitmap(foreground.toSafeBitmap(density, maxSize = 128.dp).cropBitmapTransparency())
                        } else if (iconDrawable is BitmapDrawable) {
                            qsTile?.icon = Icon.createWithBitmap(qsTile?.label?.first()?.toString()?.textAsBitmap(128f, Color.WHITE))
                        } else {
                            qsTile?.icon = Icon.createWithResource(widgetPackage, icon)
                        }
                    } else {
                        qsTile?.icon = Icon.createWithResource(widgetPackage, icon)
                    }
                }
                qsTile?.label = this.loadLabel(packageManager)
            } else {
                qsTile?.icon = Icon.createWithResource(this@BaseWidgetTile.packageName, R.drawable.ic_baseline_launch_24)
                qsTile?.label = resources.getString(R.string.app_name)
            }
        }

        qsTile?.state = if (widgetInfo != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}