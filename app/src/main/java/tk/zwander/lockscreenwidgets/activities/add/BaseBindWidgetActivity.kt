package tk.zwander.lockscreenwidgets.activities.add

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.ShortcutIdManager
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.util.toBase64

abstract class BaseBindWidgetActivity : AppCompatActivity() {
    companion object {
        const val PERM_CODE = 104
        const val CONFIG_CODE = 105
        const val REQ_CONFIG_SHORTCUT = 106
    }

    protected val widgetHost by lazy { WidgetHostCompat.getInstance(this, 1003) }
    protected val appWidgetManager by lazy { AppWidgetManager.getInstance(this)!! }
    protected val shortcutIdManager by lazy { ShortcutIdManager.getInstance(this, widgetHost) }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PERM_CODE -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return

                if (resultCode == Activity.RESULT_OK) {
                    //The user has granted permission for Lockscreen Widgets
                    //so retry binding the widget
                    tryBindWidget(
                        appWidgetManager.getAppWidgetInfo(id)
                    )
                } else {
                    //The user didn't allow Lockscreen Widgets to bind
                    //widgets, so delete the allocated ID
                    widgetHost.deleteAppWidgetId(id)
                }
            }

            CONFIG_CODE -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return
                if (id == -1) return

                if (resultCode == Activity.RESULT_OK) {
                    //Widget configuration was successful: add the
                    //widget to the frame
                    addNewWidget(id, appWidgetManager.getAppWidgetInfo(id))
                } else {
                    //Widget configuration was canceled: delete the
                    //allocated ID
                    widgetHost.deleteAppWidgetId(id)
                }
            }

            REQ_CONFIG_SHORTCUT -> {
                val shortcutIntent = data?.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)

                if (shortcutIntent != null) {
                    val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
                    val iconRes = data.getParcelableExtra<Intent.ShortcutIconResource?>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
                    val iconBmp = data.getParcelableExtra<Bitmap?>(Intent.EXTRA_SHORTCUT_ICON)

                    val shortcut = WidgetData.shortcut(
                        shortcutIdManager.allocateShortcutId(),
                        name, iconBmp.toBase64(), iconRes, shortcutIntent
                    )

                    addNewShortcut(shortcut)
                }
            }
        }
    }

    /**
     * Start the widget binding process.
     * If Lockscreen Widgets isn't allowed to bind widgets, request permission.
     * Otherwise, if the widget to be bound has a configuration Activity,
     * launch that.
     * Otherwise, just add the widget to the frame.
     *
     * @param info the widget to be bound
     * @param id the ID of the widget to be bound. If this is being called on saved
     * widgets (i.e. after an app restart), then the ID will be provided. Otherwise,
     * it will be allocated.
     */
    protected open fun tryBindWidget(
        info: AppWidgetProviderInfo,
        id: Int = widgetHost.allocateAppWidgetId()
    ) {
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)

        if (!canBind) getWidgetPermission(id, info.provider)
        else {
            //Only launch the config Activity if the widget isn't already bound (avoid reconfiguring it
            //every time the app restarts)
            if (info.configure != null && !prefManager.currentWidgets.map { it.id }.contains(id)) {
                configureWidget(id, info)
            } else {
                addNewWidget(id, info)
            }
        }
    }

    protected fun tryBindShortcut(
        info: ShortcutListInfo
    ) {
        val configureIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        configureIntent.`package` = info.shortcutInfo.activityInfo.packageName
        configureIntent.component = ComponentName(info.shortcutInfo.activityInfo.packageName,
            info.shortcutInfo.activityInfo.name)

        startActivityForResult(configureIntent, REQ_CONFIG_SHORTCUT)
    }

    /**
     * Request permission to bind widgets.
     *
     * @param id the ID of the current widget
     * @param provider the current widget's provider
     */
    protected fun getWidgetPermission(id: Int, provider: ComponentName) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        startActivityForResult(intent, PERM_CODE)
    }

    /**
     * Launch the specified widget's configuration Activity.
     *
     * @param id the ID of the widget to configure
     */
    protected open fun configureWidget(id: Int, provider: AppWidgetProviderInfo) {
        try {
            //Use the system API instead of ACTION_APPWIDGET_CONFIGURE to try to avoid some permissions issues
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0,
                CONFIG_CODE, null)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                resources.getString(
                    R.string.configure_widget_error,
                    provider
                ),
                Toast.LENGTH_LONG
            ).show()
            addNewWidget(id, provider)
        }
    }

    /**
     * Add the specified widget to the frame and save it to SharedPreferences.
     *
     * @param id the ID of the widget to be added
     */
    protected open fun addNewWidget(id: Int, provider: AppWidgetProviderInfo) {
        val widget = WidgetData.widget(
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.loadPreviewImage(this, 0).toBitmap().toBase64()
        )
        prefManager.currentWidgets = prefManager.currentWidgets.apply {
            add(widget)
        }
        finish()
    }

    protected open fun addNewShortcut(shortcut: WidgetData) {
        prefManager.currentWidgets = prefManager.currentWidgets.apply {
            add(shortcut)
        }
        finish()
    }
}