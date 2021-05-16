package tk.zwander.lockscreenwidgets.tiles.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
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
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.AddTileWidgetActivity
import tk.zwander.lockscreenwidgets.util.*
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(Build.VERSION_CODES.N)
abstract class BaseWidgetTile : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {
    protected val manager by lazy { AppWidgetManager.getInstance(this) }
    protected val iManager by lazy {
        IAppWidgetService.Stub.asInterface(
            ServiceManager.getService(Context.APPWIDGET_SERVICE)
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

            return manager.getAppWidgetInfo(widgetId)
        }
    protected val widgetPackage: String?
        get() = widgetInfo?.providerInfo?.packageName
    protected val remoteResources: Resources?
        get() {
            return try {
                val packageName = widgetPackage
                packageManager.getResourcesForApplication(packageName)
            } catch (e: Exception) {
                null
            }
        }

    protected val views by lazy { AtomicReference(generateViews()) }

    override fun onStartListening() {
        super.onStartListening()

        views.set(generateViews())
        updateTile()
    }

    open fun semGetDetailViewTitle(): CharSequence? {
        return qsTile?.label
    }

    open fun semGetDetailViewSettingButtonName(): CharSequence? {
        return "Test"
    }

    open fun semIsToggleButtonExists(): Boolean {
        return false
    }

    open fun semIsToggleButtonChecked(): Boolean {
        return false
    }

    open fun semGetDetailView(): RemoteViews? {
        views.set(generateViews())
        return views.get()
    }

    open fun semGetSettingsIntent(): Intent? {
        return AddTileWidgetActivity.createIntent(this, tileId)
    }

    open fun semSetToggleButtonChecked(checked: Boolean) {
        startActivity(semGetSettingsIntent())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PrefManager.KEY_CUSTOM_TILES) {
            updateTile()
            views.set(generateViews())
        }
    }

    private fun generateViews(): RemoteViews {
        var outerView = generateDefaultViews()

        try {
            val widgetView = iManager.getAppWidgetViews(packageName, widgetId)
            if (widgetView != null) {
                if (isDebug) {
                    Log.e(App.DEBUG_LOG_TAG, "Custom widget loaded for tile ID $tileId")
                }

                outerView = widgetView
            } else {
                if (isDebug) {
                    Log.e(App.DEBUG_LOG_TAG, "Custom widget view is null for tile ID $tileId")
                }
            }
        } catch (e: Exception) {
            if (isDebug) {
                Log.e(App.DEBUG_LOG_TAG, "Exception adding widget for tile ID $tileId", e)
            }
        }

        return outerView
    }

    private fun generateDefaultViews(): RemoteViews {
        val views = RemoteViews(packageName, R.layout.default_tile_views)
        views.setOnClickPendingIntent(
            R.id.add, PendingIntent.getActivity(
            this, 100, semGetSettingsIntent(), 0
        ))
        return views
    }

    private fun updateTile() {
        widgetInfo?.apply {
            with (remoteResources) {
                if (this != null) {
                    val iconDrawable = ResourcesCompat.getDrawable(this, icon, this.newTheme())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && iconDrawable is AdaptiveIconDrawable) {
                        val foreground = iconDrawable.foreground
                        qsTile?.icon = Icon.createWithBitmap(foreground.toBitmap(128, 128).cropBitmapTransparency())
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
        }

        qsTile?.state = if (widgetInfo != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }
}