package tk.zwander.common.activities.add

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.view.Display
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bugsnag.android.Bugsnag
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.ConfigureLauncher
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.LSDisplay
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.componentNameCompat
import tk.zwander.common.util.createPersistablePreviewBitmap
import tk.zwander.common.util.density
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.getRemoteDrawable
import tk.zwander.common.util.hasConfiguration
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.shortcutIdManager
import tk.zwander.common.util.toSafeBitmap
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.util.FramePrefs
import kotlin.math.floor

abstract class BaseBindWidgetActivity : BaseActivity() {
    companion object {
        const val EXTRA_HOLDER_ID = "holder_id"
    }

    protected val widgetHost by lazy { widgetHostCompat }

    protected abstract var currentWidgets: MutableSet<WidgetData>
    protected open val holderId by lazy { intent.getIntExtra(EXTRA_HOLDER_ID, Int.MIN_VALUE) }

    protected open val currentIds: Collection<Int>
        get() = currentWidgets.map { it.id }
    protected open val deleteOnConfigureError: Boolean = true

    protected val displayManager by lazy { getSystemService(DISPLAY_SERVICE) as DisplayManager }
    protected val display: LSDisplay by lazy {
        LSDisplay(
            display = displayManager.getDisplay(Display.DEFAULT_DISPLAY),
            fontScale = resources.configuration.fontScale,
        )
    }

    private var currentRequestId: Int? = null

    private val permRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentRequestId ?: -1) ?: currentRequestId

                if (id == null || id == -1) {
                    logUtils.debugLog("Unable to get widget ID.", null)
                    return@registerForActivityResult
                }

                if (result.resultCode == RESULT_OK) {
                    val widgetInfo = appWidgetManager.getAppWidgetInfo(id)

                    if (widgetInfo == null) {
                        logUtils.debugLog("Unable to get app widget info for ID $id", null)
                        widgetHost.deleteAppWidgetId(id)
                    } else {
                        //The user has granted permission for Lockscreen Widgets
                        //so retry binding the widget
                        tryBindWidget(widgetInfo)
                    }
                } else {
                    //The user didn't allow Lockscreen Widgets to bind
                    //widgets, so delete the allocated ID
                    widgetHost.deleteAppWidgetId(id)
                }
            } finally {
                currentRequestId = null
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
                                    iconRes?.let { getRemoteDrawable(iconRes.packageName, iconRes) }?.toSafeBitmap(density, maxSize = 128.dp)
                                } catch (_: PackageManager.NameNotFoundException) {
                                    null
                                }

                            val shortcut = WidgetData.shortcut(
                                context = this,
                                id = shortcutIdManager.allocateShortcutId(),
                                label = name,
                                icon = iconBmp,
                                iconRes = null,
                                shortcutIntent = shortcutIntent,
                                size = WidgetSizeData(1, 1)
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
                                ?.toSafeBitmap(density, maxSize = 128.dp)

                            val intent = pinItemRequest.shortcutInfo?.intent ?: run {
                                val extras = pinItemRequest.shortcutInfo?.extras ?: Bundle()
                                val number = extras["phoneNumber"]
                                val action = extras["shortcutAction"]

                                if (number != null) {
                                    Intent().apply {
                                        action?.let { this.action = it.toString() }
                                        setData(
                                            "tel://${
                                                PhoneNumberUtils.convertAndStrip(
                                                    number.toString()
                                                )
                                            }".toUri()
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
                                    this,
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

    @Suppress("LeakingThis")
    private val configureLauncher = ConfigureLauncher(
        activity = this,
        addNewWidget = ::addNewWidget,
        finishIfNoErrors = ::finishIfNoErrors,
    )

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
        id: Int = widgetHost.allocateAppWidgetId(),
    ) {
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)

        if (!canBind) getWidgetPermission(id, info.provider)
        else {
            //Only launch the config Activity if the widget isn't already bound (avoid reconfiguring it
            //every time the app restarts)
            if (info.hasConfiguration(this) && !currentIds.contains(id)) {
                configureWidget(id, info)
            } else {
                addNewWidget(id, info)
            }
        }
    }

    protected fun tryBindShortcut(
        info: ShortcutListInfo,
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

            currentRequestId = id
            permRequest.launch(intent)
        } catch (e: ActivityNotFoundException) {
            logUtils.normalLog("Unable to launch widget permission request", e)
            widgetHost.deleteAppWidgetId(id)
            pendingErrors++

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(
                    resources.getString(
                        R.string.bind_widget_error,
                        provider,
                    ),
                )
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    pendingErrors--
                }
                .show()
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
                        provider,
                    ),
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
        finishIfNoErrors()
    }

    protected open val colCount: Int
        get() = FramePrefs.getColCountForFrame(this, holderId)
    protected open val rowCount: Int
        get() = FramePrefs.getRowCountForFrame(this, holderId)
    protected open val height: Float
        get() = frameSizeAndPosition.getSizeForType(
            FrameSizeAndPosition.FrameType.LockNormal.Portrait,
            display,
        ).y
    protected open val width: Float
        get() = frameSizeAndPosition.getSizeForType(
            FrameSizeAndPosition.FrameType.LockNormal.Portrait,
            display,
        ).y

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
        overrideSize: WidgetSizeData? = null,
    ): WidgetData {
        return WidgetData.widget(
            this,
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.createPersistablePreviewBitmap(this),
            overrideSize ?: calculateInitialWidgetSize(provider)
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        configureLauncher.destroy(deleteOnConfigureError)
    }

    protected open fun addNewShortcut(shortcut: WidgetData) {
        currentWidgets = currentWidgets.apply {
            add(shortcut)
        }
        finishIfNoErrors()
    }

    private fun finishIfNoErrors() {
        if (pendingErrors == 0) {
            finish()
        }
    }
}