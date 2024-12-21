package tk.zwander.common.activities.add

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import android.telephony.PhoneNumberUtils
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import com.android.internal.appwidget.IAppWidgetService
import com.bugsnag.android.Bugsnag
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.componentNameCompat
import tk.zwander.common.util.createPersistablePreviewBitmap
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.getRemoteDrawable
import tk.zwander.common.util.getSamsungConfigureComponent
import tk.zwander.common.util.internalActivityOptions
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.shortcutIdManager
import tk.zwander.common.util.toSafeBitmap
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate
import kotlin.math.floor

abstract class BaseBindWidgetActivity : BaseActivity() {
    companion object {
        private const val CONFIGURE_REQ = 1000
    }

    protected val widgetHost by lazy { widgetHostCompat }
    protected val widgetDelegate: WidgetFrameDelegate?
        get() = WidgetFrameDelegate.peekInstance(this)

    protected abstract var currentWidgets: MutableSet<WidgetData>
    private var currentConfigId: Int? = null

    protected open val currentIds: Collection<Int>
        get() = currentWidgets.map { it.id }
    protected open val deleteOnConfigureError: Boolean = true

    private val permRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                ?: return@registerForActivityResult

            if (result.resultCode == RESULT_OK) {
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
    private val configShortcutRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            var didAddShortcut = false
            try {
                result.data?.let { data ->
                    fun addShortcutFromIntent(overrideLabel: String? = null) {
                        val shortcutIntent =
                            data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)

                        if (shortcutIntent != null) {
                            val name =
                                overrideLabel ?: data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Unknown"
                            val iconRes =
                                data.getParcelableExtra<Intent.ShortcutIconResource?>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
                            val iconBmp =
                                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON) ?: try {
                                    iconRes?.let { getRemoteDrawable(iconRes.packageName, iconRes) }?.toSafeBitmap(maxSize = 128.dp)
                                } catch (e: PackageManager.NameNotFoundException) {
                                    null
                                }

                            val shortcut = WidgetData.shortcut(
                                shortcutIdManager.allocateShortcutId(),
                                name, iconBmp, null, shortcutIntent,
                                WidgetSizeData(1, 1)
                            )

                            addNewShortcut(shortcut)
                            didAddShortcut = true
                        } else {
                            val msg = "No shortcut intent found.\n" +
                                    "Intent: ${prefManager.gson.toJson(data)}"

                            logUtils.normalLog(msg)
                            Bugsnag.notify(IllegalStateException(msg))
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pinItemRequest =
                            data.getParcelableExtra<LauncherApps.PinItemRequest>(LauncherApps.EXTRA_PIN_ITEM_REQUEST)

                        if (pinItemRequest != null) {
                            val name = pinItemRequest.shortcutInfo?.run { longLabel ?: shortLabel }
                                ?.toString() ?: "Unknown"
                            val icon = pinItemRequest.shortcutInfo?.icon
                                ?.loadDrawable(this)
                                ?.toSafeBitmap(maxSize = 128.dp)

                            val intent = pinItemRequest.shortcutInfo?.intent ?: run {
                                val extras = pinItemRequest.shortcutInfo?.extras ?: Bundle()
                                val number = extras["phoneNumber"]
                                val action = extras["shortcutAction"]

                                if (number != null) {
                                    Intent().apply {
                                        action?.let { this.action = it.toString() }
                                        setData(
                                            Uri.parse(
                                                "tel://${
                                                    PhoneNumberUtils.convertAndStrip(
                                                        number.toString()
                                                    )
                                                }"
                                            )
                                        )
                                    }
                                } else {
                                    null
                                }
                            }

                            if (intent == null) {
                                val msg = "Unable to find intent for pin request.\n" +
                                        "Request Extras: ${
                                            pinItemRequest.extras?.keySet()
                                                ?.map { it to pinItemRequest.extras?.get(it) }
                                        }\n" +
                                        "Shortcut Info Extras: ${
                                            pinItemRequest.shortcutInfo?.extras?.keySet()
                                                ?.map { it to pinItemRequest.shortcutInfo?.extras?.get(it) }
                                        }\n" +
                                        "Shortcut Info: ${pinItemRequest.shortcutInfo?.toInsecureString()}"

                                if (!data.hasExtra(Intent.EXTRA_SHORTCUT_INTENT)) {
                                    logUtils.normalLog(msg)
                                    Bugsnag.notify(Exception(msg))
                                } else {
                                    logUtils.debugLog(msg)
                                }
                            } else {
                                val shortcut = WidgetData.shortcut(
                                    shortcutIdManager.allocateShortcutId(),
                                    name, icon, null, intent,
                                    WidgetSizeData(1, 1),
                                )

                                addNewShortcut(shortcut)
                                didAddShortcut = true

                                return@registerForActivityResult
                            }
                        }

                        addShortcutFromIntent(
                            pinItemRequest?.shortcutInfo?.run {
                                longLabel.takeIf { !it.isNullOrEmpty() } ?: shortLabel
                            }?.toString()
                        )
                    } else {
                        addShortcutFromIntent()
                    }
                }
            } catch (e: Exception) {
                logUtils.normalLog("Error configuring shortcut.", e)
            }

            if (!didAddShortcut) {
                Toast.makeText(this, R.string.failed_to_add_shortcut, Toast.LENGTH_SHORT).show()
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

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
        } catch (e: Throwable) {
            logUtils.debugLog("Unable to create shortcut", e)

            pendingErrors++

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(
                    resources.getString(
                        R.string.create_shortcut_error,
                        e.message
                    )
                )
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
        try {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

            permRequest.launch(intent)
        } catch (e: ActivityNotFoundException) {
            logUtils.normalLog("Unable to launch widget permission request", e)
            widgetHost.deleteAppWidgetId(id)
        }
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
                .setMessage(
                    resources.getString(
                        R.string.configure_widget_error,
                        provider
                    )
                )
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
        get() = frameSizeAndPosition.getSizeForType(FrameSizeAndPosition.FrameType.LockNormal.Portrait).y
    protected open val width: Float
        get() = frameSizeAndPosition.getSizeForType(FrameSizeAndPosition.FrameType.LockNormal.Portrait).y

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

    protected open fun createWidgetData(
        id: Int,
        provider: AppWidgetProviderInfo,
        overrideSize: WidgetSizeData? = null
    ): WidgetData {
        return WidgetData.widget(
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.createPersistablePreviewBitmap(this),
            overrideSize ?: calculateInitialWidgetSize(provider)
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        if (deleteOnConfigureError) {
            currentConfigId?.let {
                //Widget configuration was canceled: delete the
                //allocated ID
                widgetHost.deleteAppWidgetId(it)
            }
        }
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
        private val configLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                onActivityResult(CONFIGURE_REQ, result.resultCode, result.data)
            }
        private val samsungConfigLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                onActivityResult(CONFIGURE_REQ, result.resultCode, result.data)
            }

        @SuppressLint("NewApi")
        fun launch(id: Int): Boolean {
            try {
                val samsungConfigComponent = appWidgetManager.getAppWidgetInfo(id)
                    .getSamsungConfigureComponent(this@BaseBindWidgetActivity)

                logUtils.debugLog("Found Samsung config component $samsungConfigComponent.")

                if (samsungConfigComponent != null) {
                    val launchIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    launchIntent.component = samsungConfigComponent
                    launchIntent.putExtra("appWidgetId", id)

                    currentConfigId = id
                    samsungConfigLauncher.launch(launchIntent)
                    return true
                }
            } catch (e: Throwable) {
                logUtils.normalLog("Error configuring Samsung widget", e)
            }

            //Use the system API instead of ACTION_APPWIDGET_CONFIGURE to try to avoid some permissions issues
            try {
                val intentSender =
                    IAppWidgetService.Stub.asInterface(ServiceManager.getService(Context.APPWIDGET_SERVICE))
                        .createAppWidgetConfigIntentSender(opPackageName, id, 0)

                logUtils.debugLog("Intent sender is $intentSender")

                if (intentSender != null) {
                    configLauncher.launch(
                        IntentSenderRequest.Builder(intentSender)
                            .build(),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ActivityOptionsCompat.makeBasic()
                                .apply {
                                    internalActivityOptions?.setPendingIntentBackgroundActivityStartMode(
                                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                    )
                                }
                        } else {
                            null
                        },
                    )
                    currentConfigId = id
                    return true
                }
            } catch (e: Throwable) {
                logUtils.normalLog("Unable to launch widget config IntentSender", e)
            }

            try {
                currentConfigId = id
                widgetHost.startAppWidgetConfigureActivityForResult(
                    this@BaseBindWidgetActivity,
                    id, 0, CONFIGURE_REQ,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ActivityOptions
                            .makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            .toBundle()
                    } else null,
                )
                return true
            } catch (e: Throwable) {
                logUtils.normalLog("Unable to startAppWidgetConfigureActivityForResult", e)
            }

            return false
        }

        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == CONFIGURE_REQ) {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentConfigId ?: -1)
                        ?: currentConfigId

                logUtils.debugLog("Configure complete for id $id $currentConfigId", null)

                if (resultCode == RESULT_OK && id != null && id != -1) {
                    logUtils.debugLog("Successfully configured widget.", null)

                    val widgetInfo = appWidgetManager.getAppWidgetInfo(id)

                    if (widgetInfo == null) {
                        logUtils.debugLog("Unable to get widget info for $id, not adding", null)
                        if (pendingErrors == 0) {
                            finish()
                        }
                        return
                    }

                    currentConfigId = null

                    addNewWidget(id, widgetInfo)
                } else {
                    logUtils.debugLog("Failed to configure widget. Result code $resultCode, id $id.", null)
                    if (pendingErrors == 0) {
                        finish()
                    }
                }
            }
        }
    }
}