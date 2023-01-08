package tk.zwander.common.activities.add

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.logUtils

abstract class ReconfigureWidgetActivity : BaseBindWidgetActivity() {
    companion object {
        const val EXTRA_PREVIOUS_ID = "previous_id"
        const val EXTRA_PROVIDER_INFO = "provider_info"
    }

    private val prevId by lazy { intent.getIntExtra(EXTRA_PREVIOUS_ID, -1) }
    private val providerInfo by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PROVIDER_INFO, AppWidgetProviderInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROVIDER_INFO) as? AppWidgetProviderInfo?
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prevId == -1 || providerInfo == null) {
            finish()
            return
        }

        DismissOrUnlockActivity.launch(this)

        tryBindWidget(providerInfo!!, widgetHost.allocateAppWidgetId())
    }

    @SuppressLint("NewApi")
    override fun tryBindWidget(info: AppWidgetProviderInfo, id: Int) {
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(id, info.provider)

        if (!canBind) getWidgetPermission(id, info.provider)
        else {
            if (info.configure != null) {
                configureWidget(id, info)
            } else {
                addNewWidget(id, info)
            }
        }
    }

    override fun configureWidget(id: Int, provider: AppWidgetProviderInfo) {
        DismissOrUnlockActivity.launch(this, false)
        super.configureWidget(id, provider)
    }

    override fun addNewWidget(id: Int, provider: AppWidgetProviderInfo) {
        val newSet = currentWidgets.toMutableList()

        val oldWidgetIndex = newSet.indexOf(
            WidgetData.widget(
                prevId,
                provider.provider,
                "", null,
                WidgetSizeData(1, 1)
            )
        )

        val oldWidget = if (oldWidgetIndex != -1) newSet.removeAt(oldWidgetIndex) else null

        val widget = createWidgetData(id, provider, oldWidget?.safeSize)

        val safeIndex = if (oldWidgetIndex != -1) oldWidgetIndex else newSet.lastIndex.run { if (this == -1) 0 else this }

        newSet.add(safeIndex, widget)

        logUtils.normalLog("Removed old widget from $oldWidgetIndex ($safeIndex) and added new one $widget")

        currentWidgets = LinkedHashSet(newSet)

        finish()
    }
}