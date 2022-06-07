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
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetSizeData
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.floor

abstract class BaseBindWidgetActivity : AppCompatActivity() {
    companion object {
        private const val CONFIGURE_REQ = 1000
    }

    protected val widgetHost by lazy { WidgetHostCompat.getInstance(this, 1003) }
    protected val appWidgetManager by lazy { AppWidgetManager.getInstance(this)!! }
    protected val shortcutIdManager by lazy { ShortcutIdManager.getInstance(this, widgetHost) }
    protected val widgetDelegate: WidgetFrameDelegate?
        get() = WidgetFrameDelegate.peekInstance(this)

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

    private val configureLauncher = ConfigureLauncher()

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        configureLauncher.onActivityResult(requestCode, resultCode, data)
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
        try {
            val configureIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
            configureIntent.`package` = info.shortcutInfo.activityInfo.packageName
            configureIntent.component = ComponentName(info.shortcutInfo.activityInfo.packageName,
                info.shortcutInfo.activityInfo.name)

            configShortcutRequest.launch(configureIntent)
        } catch (e: SecurityException) {
            logUtils.debugLog("Unable to create shortcut", e)

            Toast.makeText(this, R.string.create_shortcut_error, Toast.LENGTH_SHORT).show()
        }
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

    /**
     * Launch the specified widget's configuration Activity.
     *
     * @param id the ID of the widget to configure
     */
    @SuppressLint("NewApi")
    protected open fun configureWidget(id: Int, provider: AppWidgetProviderInfo) {
        if (!configureLauncher.launch(id)) {
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
        prefManager.currentWidgets = prefManager.currentWidgets.apply {
            add(createWidgetData(id, provider))
        }
        finish()
    }

    protected open val colCount: Int
        get() = prefManager.frameColCount
    protected open val rowCount: Int
        get() = prefManager.frameRowCount
    protected open val height: Float
        get() = prefManager.frameHeightDp
    protected open val width: Float
        get() = prefManager.frameWidthDp

    protected open fun createWidgetData(id: Int, provider: AppWidgetProviderInfo, overrideSize: WidgetSizeData? = null): WidgetData {
        return WidgetData.widget(
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.loadPreviewOrIcon(this, 0)?.toBitmap(
                maxWidth = 512,
                maxHeight = 512,
            ).toBase64(),
            overrideSize ?: run {
                val widthRatio = provider.minWidth.toFloat() / width
                val defaultColSpan = floor((widthRatio * colCount)).toInt()
                    .coerceAtMost(colCount).coerceAtLeast(1)

                val heightRatio = provider.minHeight.toFloat() / height
                val defaultRowSpan = floor((heightRatio * rowCount)).toInt()
                    .coerceAtMost(rowCount).coerceAtLeast(1)

                WidgetSizeData(defaultColSpan, defaultRowSpan)
            }
        )
    }

    protected open fun addNewShortcut(shortcut: WidgetData) {
        prefManager.currentWidgets = prefManager.currentWidgets.apply {
            add(shortcut)
        }
        finish()
    }

    private inner class ConfigureLauncher {
        private val configLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            onActivityResult(CONFIGURE_REQ, result.resultCode, result.data)
        }

        private var currentConfigId: Int? = null

        @SuppressLint("NewApi")
        fun launch(id: Int): Boolean {
            //Use the system API instead of ACTION_APPWIDGET_CONFIGURE to try to avoid some permissions issues
            try {
                val intentSender = IAppWidgetService.Stub.asInterface(ServiceManager.getService(Context.APPWIDGET_SERVICE))
                    .createAppWidgetConfigIntentSender(opPackageName, id, 0)

                if (intentSender != null) {
                    configLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    currentConfigId = id
                    return true
                }
            } catch (e: Exception) {
                logUtils.debugLog("Unable to launch widget config IntentSender", e)
            }

            try {
                widgetHost.startAppWidgetConfigureActivityForResult(
                    this@BaseBindWidgetActivity,
                    id, 0, CONFIGURE_REQ, null
                )
                currentConfigId = id
                return true
            } catch (e: Exception) {
                logUtils.debugLog("Unable to startAppWidgetConfigureActivityForResult", e)
            }

            return false
        }

        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == CONFIGURE_REQ) {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

                if (resultCode == Activity.RESULT_OK && id != null && id != -1) {
                    logUtils.debugLog("Successfully configured widget.")
                    //Widget configuration was successful: add the
                    //widget to the frame
                    addNewWidget(id, appWidgetManager.getAppWidgetInfo(id) ?: run {
                        logUtils.debugLog("Unable to get widget info for $id, not adding")
                        currentConfigId?.let {
                            widgetHost.deleteAppWidgetId(it)
                        }
                        return
                    })
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
        }
    }
}