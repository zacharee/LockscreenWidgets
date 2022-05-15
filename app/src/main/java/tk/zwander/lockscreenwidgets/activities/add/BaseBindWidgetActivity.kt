package tk.zwander.lockscreenwidgets.activities.add

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.ServiceManager
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetSizeData
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.*

abstract class BaseBindWidgetActivity : AppCompatActivity() {
    protected val widgetHost by lazy { WidgetHostCompat.getInstance(this, 1003) }
    protected val appWidgetManager by lazy { AppWidgetManager.getInstance(this)!! }
    protected val shortcutIdManager by lazy { ShortcutIdManager.getInstance(this, widgetHost) }

    private val permRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
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

    @Suppress("DEPRECATION")
    private val configShortcutRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val shortcutIntent = result.data?.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)

        if (shortcutIntent != null) {
            val name = result.data!!.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
            val iconRes = result.data!!.getParcelableExtra<Intent.ShortcutIconResource?>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
            val iconBmp = result.data!!.getParcelableExtra<Bitmap?>(Intent.EXTRA_SHORTCUT_ICON)

            val shortcut = WidgetData.shortcut(
                shortcutIdManager.allocateShortcutId(),
                name, iconBmp.toBase64(), iconRes, shortcutIntent,
                WidgetSizeData(1, 1)
            )

            addNewShortcut(shortcut)
        }
    }

    protected var currentConfigId: Int? = null

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

        configShortcutRequest.launch(configureIntent)
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

        permRequest.launch(intent)
    }

    protected open val configLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        if (result.resultCode == Activity.RESULT_OK && id != null && id != -1) {
            logUtils.debugLog("Successfully configured widget.")
            //Widget configuration was successful: add the
            //widget to the frame
            addNewWidget(id, appWidgetManager.getAppWidgetInfo(id))
        } else {
            logUtils.debugLog("Failed to configure widget.")
            currentConfigId?.let {
                //Widget configuration was canceled: delete the
                //allocated ID
                widgetHost.deleteAppWidgetId(it)
            }
        }

        currentConfigId = null
    }

    /**
     * Launch the specified widget's configuration Activity.
     *
     * @param id the ID of the widget to configure
     */
    @SuppressLint("NewApi")
    protected open fun configureWidget(id: Int, provider: AppWidgetProviderInfo) {
        //Use the system API instead of ACTION_APPWIDGET_CONFIGURE to try to avoid some permissions issues
        val intentSender = IAppWidgetService.Stub.asInterface(ServiceManager.getService(Context.APPWIDGET_SERVICE))
            .createAppWidgetConfigIntentSender(opPackageName, id, 0)

        if (intentSender != null) {
            try {
                currentConfigId = id
                configLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                return
            } catch (_: Exception) {}
        }

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
            provider.loadPreviewOrIcon(this, 0)?.toBitmap().toBase64(),
            WidgetSizeData(1, 1)
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