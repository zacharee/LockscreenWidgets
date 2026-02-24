package tk.zwander.lockscreenwidgets.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import android.util.SizeF
import android.util.SparseArray
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import androidx.core.os.BundleCompat
import androidx.core.util.forEach
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.common.appwidget.RemoteViewsProxyService
import tk.zwander.common.data.WidgetData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.getRemoteViewsToApplyCompat
import tk.zwander.common.util.matches
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.WidgetStackConfigure
import kotlin.math.roundToInt

class WidgetStackProvider : AppWidgetProvider() {
    private val appWidgetService by lazy {
        IAppWidgetService.Stub.asInterface(
            ServiceManager.getService(Context.APPWIDGET_SERVICE),
        )
    }

    private var fromHost = false

    override fun onReceive(context: Context, intent: Intent) {
        fromHost = intent.getBooleanExtra(EXTRA_FROM_HOST, false)

        if (intent.action == ACTION_SWAP_INDEX) {
            val backward = intent.getBooleanExtra(EXTRA_BACKWARD, false)
            val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)

            widgetIds?.forEach { widgetId ->
                val allStacks = context.prefManager.widgetStackWidgets
                val stackedWidgets = (allStacks[widgetId] ?: LinkedHashSet()).toList()

                val index = (context.prefManager.widgetStackIndices[widgetId] ?: 0)
                    .coerceAtMost(stackedWidgets.lastIndex)

                val newIndex = if (backward) {
                    if (index - 1 >= 0) {
                        index - 1
                    } else {
                        stackedWidgets.lastIndex
                    }
                } else {
                    if (index + 1 <= stackedWidgets.lastIndex) {
                        index + 1
                    } else {
                        0
                    }
                }

                context.prefManager.widgetStackIndices = context.prefManager.widgetStackIndices.apply {
                    this[widgetId] = newIndex
                }
            }

            onReceive(context, intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE))
        }

        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val stackedWidgets = (context.prefManager.widgetStackWidgets[appWidgetId] ?: LinkedHashSet()).toList()
            val index = (context.prefManager.widgetStackIndices[appWidgetId] ?: 0)
                .coerceAtMost(stackedWidgets.lastIndex)
                .coerceAtLeast(0)
            val widgetData = stackedWidgets.getOrNull(index)
            val widgetView =
                widgetData?.let {
                    appWidgetService.getAppWidgetViews(context.packageName, it.id)
                }

            val allViews = widgetView?.let {
                val outerView = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val sizes = BundleCompat.getParcelableArrayList(
                        options,
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        SizeF::class.java
                    ) ?: listOf(
                        SizeF(
                            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat(),
                            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat(),
                        ),
                    )

                    sizes.associateWith { size ->
                        createViewsForSize(
                            context = context,
                            appWidgetManager = appWidgetManager,
                            stackId = appWidgetId,
                            size = size,
                            options = options,
                            widgetData = widgetData,
                            index = index,
                            stackSize = stackedWidgets.size,
                            rootWidgetViews = widgetView,
                        )
                    }.takeIf { it.isNotEmpty() }?.let {
                        RemoteViews(it)
                    }
                } else {
                    createViewsForSize(
                        context = context,
                        appWidgetManager = appWidgetManager,
                        stackId = appWidgetId,
                        size = null,
                        options = options,
                        widgetData = widgetData,
                        index = index,
                        stackSize = stackedWidgets.size,
                        rootWidgetViews = widgetView,
                    )
                } ?: return@forEach

                hoistWidgetData(context, widgetView, outerView, widgetData.id)

                outerView
            } ?: run {
                val view = RemoteViews(context.packageName, R.layout.stack_widget)
                val addWidgetView = RemoteViews(context.packageName, R.layout.add_widget)
                addWidgetView.setOnClickPendingIntent(
                    R.id.add_widget,
                    PendingIntentCompat.getActivity(
                        context,
                        appWidgetId,
                        Intent(context, WidgetStackConfigure::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        0,
                        false,
                    ),
                )
                view.removeAllViews(R.id.widget_content_even)
                view.removeAllViews(R.id.widget_content_odd)
                view.addView(R.id.widget_content_even, addWidgetView)

                view.removeAllViews(R.id.stack_dot_row)
                view.removeFromParent(R.id.stack_backward)
                view.removeFromParent(R.id.stack_forward)
                view.removeFromParent(R.id.stack_configure)

                view
            }

            App.instance.updateAutoChangeForStack(appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, allViews)
        }
    }

    private fun createViewsForSize(
        context: Context,
        appWidgetManager: AppWidgetManager,
        stackId: Int,
        size: SizeF?,
        options: Bundle?,
        widgetData: WidgetData,
        index: Int,
        stackSize: Int,
        rootWidgetViews: RemoteViews,
    ): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.stack_widget)
        val stackForward = RemoteViews(context.packageName, R.layout.stack_forward)
        val stackBackward = RemoteViews(context.packageName, R.layout.stack_backward)
        val stackConfigure = RemoteViews(context.packageName, R.layout.stack_configure)

        stackForward.setOnClickPendingIntent(
            R.id.stack_forward,
            PendingIntentCompat.getBroadcast(
                context,
                stackId,
                createSwapIntent(context, intArrayOf(stackId), false),
                0,
                false,
            )
        )
        stackBackward.setOnClickPendingIntent(
            R.id.stack_backward,
            PendingIntentCompat.getBroadcast(
                context,
                stackId + 100000,
                createSwapIntent(context, intArrayOf(stackId), true),
                0,
                false,
            ),
        )

        stackConfigure.setOnClickPendingIntent(
            R.id.stack_configure,
            PendingIntentCompat.getActivity(
                context,
                stackId + 10000,
                Intent(context, WidgetStackConfigure::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, stackId),
                0,
                false,
            ),
        )

        view.removeAllViews(R.id.stack_dot_row)

        repeat(stackSize) {
            val dot = RemoteViews(context.packageName, R.layout.widget_stack_page_dot)
            dot.setImageViewResource(
                R.id.page_dot,
                if (index == it) {
                    R.drawable.circle
                } else {
                    R.drawable.circle_inactive
                },
            )
            view.addView(R.id.stack_dot_row, dot)
        }

        val viewsToApply = rootWidgetViews.getRemoteViewsToApplyCompat(context, size)

        val rem = index % 3

        when (rem) {
            0 -> {
                view.removeAllViews(R.id.widget_content_even)
                view.addView(R.id.widget_content_even, viewsToApply)
            }

            1 -> {
                view.removeAllViews(R.id.widget_content_odd)
                view.addView(R.id.widget_content_odd, viewsToApply)
            }

            2 -> {
                view.removeAllViews(R.id.widget_content_third)
                view.addView(R.id.widget_content_third, viewsToApply)
            }
        }

        view.setDisplayedChild(R.id.widget_content, rem)

        val optionsToApply = options?.apply {
            val optionsWidth = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
            val optionsHeight = getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()

            val (realMaxWidth, realMaxHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val allSizes = BundleCompat.getParcelableArrayList(
                    this,
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    SizeF::class.java,
                )

                (allSizes?.minOf { it.width } ?: optionsWidth) to (allSizes?.maxOf { it.height } ?: optionsHeight)
            } else {
                optionsWidth to optionsHeight
            }

            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, realMaxWidth.roundToInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, realMaxWidth.roundToInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, realMaxHeight.roundToInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, realMaxHeight.roundToInt())
        }

        if (optionsToApply?.matches(appWidgetManager.getAppWidgetOptions(widgetData.id)) != true) {
            appWidgetManager.updateAppWidgetOptions(widgetData.id, optionsToApply)
        }

        view.addView(R.id.widget_root, stackForward)
        view.addView(R.id.widget_root, stackBackward)
        view.addView(R.id.widget_root, stackConfigure)

        hoistWidgetData(context, viewsToApply, view, widgetData.id)

        return view
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        onUpdate(
            context,
            appWidgetManager,
            intArrayOf(appWidgetId),
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        appWidgetIds.forEach { appWidgetId ->
            context.widgetHostCompat.deleteAppWidgetId(appWidgetId)
        }
    }

    companion object {
        const val ACTION_SWAP_INDEX = "${BuildConfig.APPLICATION_ID}.intent.action.SWAP_INDEX"
        const val EXTRA_FROM_HOST = "from_host"
        const val EXTRA_BACKWARD = "backward"

        fun update(context: Context, ids: IntArray, fromHost: Boolean = false) {
            context.sendBroadcast(
                createBaseIntent(context, ids)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(EXTRA_FROM_HOST, fromHost),
            )
        }

        fun updateOptions(context: Context, ids: IntArray, options: Bundle?) {
            context.sendBroadcast(
                createBaseIntent(context, ids)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options),
            )
        }

        fun createSwapIntent(context: Context, ids: IntArray, backward: Boolean): Intent {
            return createBaseIntent(context, ids)
                .setAction(ACTION_SWAP_INDEX)
                .putExtra(EXTRA_BACKWARD, backward)
        }

        private fun createBaseIntent(context: Context, ids: IntArray): Intent {
            return Intent(context, WidgetStackProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }

        @Suppress("UNCHECKED_CAST")
        private fun hoistWidgetData(
            context: Context,
            innerView: RemoteViews,
            outerView: RemoteViews,
            innerWidgetId: Int,
        ) {
            val sourceActions = innerView::class.java.getDeclaredField("mActions")
                .apply { isAccessible = true }
                .get(innerView) as? MutableList<Any>

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && innerView.hasLegacyLists()) {
                val collectionCacheSource = innerView::class.java.getDeclaredField("mCollectionCache")
                    .apply { isAccessible = true }
                    .get(innerView)
                val collectionCacheDest = outerView::class.java.getDeclaredField("mCollectionCache")
                    .apply { isAccessible = true }
                    .get(outerView)

                val sourceIdToUriMapping = collectionCacheSource::class.java.getDeclaredField("mIdToUriMapping")
                    .apply { isAccessible = true }
                    .get(collectionCacheSource) as SparseArray<String>
                val sourceUriToCollectionMapping = collectionCacheSource::class.java.getDeclaredField("mUriToCollectionMapping")
                    .apply { isAccessible = true }
                    .get(collectionCacheSource) as Map<String, *>

                sourceIdToUriMapping.forEach { intentId, uri ->
                    val items = sourceUriToCollectionMapping[uri]!!

                    collectionCacheDest::class.java.getDeclaredMethod(
                        "addMapping",
                        Int::class.java,
                        String::class.java,
                        items::class.java,
                    ).apply {
                        isAccessible = true
                    }.invoke(
                        collectionCacheDest,
                        intentId,
                        uri,
                        items,
                    )
                }
            }

            val collectionActions = (sourceActions?.filter { action ->
                action::class.java.name.contains("SetRemoteCollectionItemListAdapterAction")
                        || action::class.java.name.contains("SetRemoteViewsAdapterIntent")
            } ?: listOf()).map { action ->
                if (action::class.java.name.contains("SetRemoteViewsAdapterIntent")) {
                    val wrappedIntentField = try {
                        action::class.java.getDeclaredField("mIntent")
                    } catch (_: NoSuchFieldException) {
                        action::class.java.getDeclaredField("intent")
                    }.apply { isAccessible = true }
                    val wrappedIntent = wrappedIntentField.get(action) as? Intent
                    val newIntent = RemoteViewsProxyService.createProxyIntent(
                        context = context,
                        widgetId = innerWidgetId,
                        widgetIntent = wrappedIntent,
                    )
                    wrappedIntentField.set(action, newIntent)
                }

                action
            }

            val destActionsField = outerView::class.java.getDeclaredField("mActions")
                .apply { isAccessible = true }

            if (destActionsField.get(outerView) == null) {
                destActionsField.set(outerView, collectionActions)
            } else {
                (destActionsField.get(outerView) as? MutableList<Any>)?.addAll(collectionActions)
            }

            sourceActions?.removeAll { collectionActions.contains(it) }
        }
    }
}
