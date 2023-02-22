package tk.zwander.common.activities.add

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.ServiceManager
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.android.internal.appwidget.IAppWidgetService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.componentNameCompat
import tk.zwander.common.util.loadPreviewOrIcon
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.shortcutIdManager
import tk.zwander.common.util.toBase64
import tk.zwander.common.util.toBitmap
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.floor

abstract class BaseBindWidgetActivity : ComponentActivity() {
    companion object {
        private const val CONFIGURE_REQ = 1000
    }

    protected val widgetHost by lazy { widgetHostCompat }
    protected val widgetDelegate: WidgetFrameDelegate?
        get() = WidgetFrameDelegate.peekInstance(this)

    protected abstract var currentWidgets: MutableSet<WidgetData>

    protected open val currentIds: Collection<Int>
        get() = currentWidgets.map { it.id }
    protected open val deleteOnConfigureError: Boolean = true

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
        try {
            result.data?.let { data ->
                data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)?.let { shortcutIntent ->
                    val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
                    val iconRes =
                        data.getParcelableExtra<Intent.ShortcutIconResource?>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
                    val iconBmp = data.getParcelableExtra<Bitmap?>(Intent.EXTRA_SHORTCUT_ICON)

                    val shortcut = WidgetData.shortcut(
                        shortcutIdManager.allocateShortcutId(),
                        name, iconBmp.toBase64(), iconRes, shortcutIntent,
                        WidgetSizeData(1, 1)
                    )

                    addNewShortcut(shortcut)
                }
            }
        } catch (e: Exception) {
            logUtils.normalLog("Error configuring shortcut.", e)
        }
    }

    private val configureLauncher = ConfigureLauncher()

    private var pendingErrors = 0
        set(value) {
            val oldValue = field

            field = value

            if (value == 0 && oldValue > 0) {
                finish()
            }
        }

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
            if (info.configure != null && !currentIds.contains(id)) {
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
            configureIntent.`package` = info.itemInfo.activityInfo.packageName
            configureIntent.component = info.itemInfo.activityInfo.componentNameCompat

            configShortcutRequest.launch(configureIntent)
        } catch (e: SecurityException) {
            logUtils.debugLog("Unable to create shortcut", e)

            pendingErrors++

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(resources.getString(
                    R.string.create_shortcut_error,
                    e.message
                ))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    pendingErrors--
                }
                .show()
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
            pendingErrors++

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(resources.getString(
                    R.string.configure_widget_error,
                    provider
                ))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    pendingErrors--
                }
                .show()

            addNewWidget(id, provider)
        }
    }

    /**
     * Add the specified widget to the frame and save it to SharedPreferences.
     *
     * @param id the ID of the widget to be added
     */
    protected open fun addNewWidget(id: Int, provider: AppWidgetProviderInfo) {
        currentWidgets = currentWidgets.apply {
            add(createWidgetData(id, provider))
        }
        if (pendingErrors == 0) {
            finish()
        }
    }

    protected open val colCount: Int
        get() = prefManager.frameColCount
    protected open val rowCount: Int
        get() = prefManager.frameRowCount
    protected open val height: Float
        get() = prefManager.frameHeightDp
    protected open val width: Float
        get() = prefManager.frameWidthDp

    protected open fun calculateInitialWidgetColSpan(provider: AppWidgetProviderInfo): Int {
        val widthRatio = provider.minWidth.toFloat() / width
        return floor((widthRatio * colCount)).toInt()
            .coerceAtMost(colCount).coerceAtLeast(1)
    }

    protected open fun calculateInitialWidgetRowSpan(provider: AppWidgetProviderInfo): Int {
        val heightRatio = provider.minHeight.toFloat() / height
        return floor((heightRatio * rowCount)).toInt()
            .coerceAtMost(rowCount).coerceAtLeast(1)
    }

    protected open fun calculateInitialWidgetSize(provider: AppWidgetProviderInfo): WidgetSizeData {
        val defaultColSpan = calculateInitialWidgetColSpan(provider)
        val defaultRowSpan = calculateInitialWidgetRowSpan(provider)

        return WidgetSizeData(defaultColSpan, defaultRowSpan)
    }

    protected open fun createWidgetData(id: Int, provider: AppWidgetProviderInfo, overrideSize: WidgetSizeData? = null): WidgetData {
        return WidgetData.widget(
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.loadPreviewOrIcon(this, 0)?.toBitmap(
                maxWidth = 512,
                maxHeight = 512,
            ).toBase64(),
            overrideSize ?: calculateInitialWidgetSize(provider)
        )
    }

    protected open fun addNewShortcut(shortcut: WidgetData) {
        currentWidgets = currentWidgets.apply {
            add(shortcut)
        }
        if (pendingErrors == 0) {
            finish()
        }
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

                logUtils.debugLog("Intent sender is $intentSender")

                if (intentSender != null) {
                    configLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    currentConfigId = id
                    return true
                }
            } catch (e: Throwable) {
                logUtils.normalLog("Unable to launch widget config IntentSender", e)
            }

            try {
                widgetHost.startAppWidgetConfigureActivityForResult(
                    this@BaseBindWidgetActivity,
                    id, 0, CONFIGURE_REQ, null
                )
                currentConfigId = id
                return true
            } catch (e: Throwable) {
                logUtils.normalLog("Unable to startAppWidgetConfigureActivityForResult", e)
            }

            return false
        }

        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == CONFIGURE_REQ) {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentConfigId ?: -1) ?: currentConfigId

                if (resultCode == Activity.RESULT_OK && id != null && id != -1) {
                    logUtils.debugLog("Successfully configured widget.")
                    //Widget configuration was successful: add the
                    //widget to the frame
                    addNewWidget(id, appWidgetManager.getAppWidgetInfo(id) ?: run {
                        logUtils.debugLog("Unable to get widget info for $id, not adding")
                        if (deleteOnConfigureError) {
                            currentConfigId?.let {
                                widgetHost.deleteAppWidgetId(it)
                            }
                        }
                        if (pendingErrors == 0) {
                            finish()
                        }
                        return
                    })
                } else {
                    logUtils.debugLog("Failed to configure widget.")
                    if (deleteOnConfigureError) {
                        currentConfigId?.let {
                            //Widget configuration was canceled: delete the
                            //allocated ID
                            widgetHost.deleteAppWidgetId(it)
                        }
                    }
                    if (pendingErrors == 0) {
                        finish()
                    }
                }

                currentConfigId = null
            }
        }
    }
}